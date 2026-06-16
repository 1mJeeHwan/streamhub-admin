import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";
import { getSession, signOut } from "next-auth/react";

import type { ApiResponse } from "@/types/api";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL;

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
}

export const axiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
});

async function forceSignOut(): Promise<void> {
  await signOut({ callbackUrl: "/login" });
}

/**
 * Attach the access token from the active NextAuth session.
 *
 * Reading the session triggers NextAuth's jwt callback server-side, which
 * proactively rotates the access token when it is near expiry — so the token
 * attached here is always fresh. If that rotation failed, the session carries
 * an error and we sign out instead of sending a doomed request.
 */
axiosInstance.interceptors.request.use(async (config) => {
  const session = await getSession();
  if (session?.error) {
    await forceSignOut();
    throw new axios.Cancel("session expired");
  }
  if (session?.accessToken) {
    config.headers.set("Authorization", `Bearer ${session.accessToken}`);
  }
  return config;
});

/**
 * A 401 here means the token was rejected despite proactive rotation (e.g. the
 * refresh token was revoked). Re-read the session once — which gives the jwt
 * callback a final chance to rotate — and retry; otherwise sign out.
 */
axiosInstance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const original = error.config as RetriableConfig | undefined;

    if (!original || original._retried || error.response?.status !== 401) {
      return Promise.reject(error);
    }
    original._retried = true;

    const session = await getSession();
    if (!session || session.error || !session.accessToken) {
      await forceSignOut();
      return Promise.reject(error);
    }
    original.headers.set("Authorization", `Bearer ${session.accessToken}`);
    return axiosInstance(original);
  },
);

/**
 * customInstance is the Orval mutator. Generated react-query hooks call this
 * with a partial AxiosRequestConfig and expect the unwrapped response data.
 */
export const customInstance = async <T>(
  config: AxiosRequestConfig,
): Promise<T> => {
  const response: AxiosResponse<T> = await axiosInstance(config);
  return response.data;
};

export default customInstance;
