import type {
  ContentDetail,
  ContentListItem,
  ContentType,
  HomeBundle,
  InfinityList,
  MemberAuthResponse,
  MemberInfo,
  PostDetail,
  PostListItem,
  ResultDTO,
} from "./types";

const BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

/** Error carrying the HTTP status so callers can distinguish 404/401 from other failures. */
export class ApiError extends Error {
  readonly status: number;
  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  token?: string;
}

/**
 * Low-level public API request: unwraps the ResultDTO envelope, throws ApiError with the
 * HTTP status on failure. Exported so sibling modules (e.g. albums.ts) reuse one fetch path.
 */
export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (opts.body !== undefined) headers["Content-Type"] = "application/json";
  if (opts.token) headers["Authorization"] = `Bearer ${opts.token}`;

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, {
      method: opts.method ?? "GET",
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    });
  } catch {
    throw new ApiError("서버에 연결할 수 없습니다.", 0);
  }

  let envelope: ResultDTO<T> | null = null;
  try {
    envelope = (await res.json()) as ResultDTO<T>;
  } catch {
    envelope = null;
  }

  if (!res.ok) {
    throw new ApiError(envelope?.resultMessage ?? `요청에 실패했습니다 (${res.status})`, res.status);
  }
  if (!envelope || envelope.resultCode !== "0000") {
    throw new ApiError(envelope?.resultMessage ?? "알 수 없는 오류가 발생했습니다.", res.status);
  }
  return envelope.resultObject;
}

/** Build a `?a=1&b=2` query string, dropping undefined/empty/null values. */
export function query(params: Record<string, string | number | undefined>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== "" && v !== null) sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : "";
}

export interface ContentListParams {
  type?: ContentType;
  keyword?: string;
  pageNumber?: number;
  pageSize?: number;
}

export interface PostListParams {
  keyword?: string;
  pageNumber?: number;
  pageSize?: number;
}

export const api = {
  home: () => request<HomeBundle>("/pub/v1/home"),
  contents: (p: ContentListParams = {}) =>
    request<InfinityList<ContentListItem>>(`/pub/v1/contents${query({ ...p })}`),
  content: (id: number) => request<ContentDetail>(`/pub/v1/contents/${id}`),
  posts: (p: PostListParams = {}) =>
    request<InfinityList<PostListItem>>(`/pub/v1/posts${query({ ...p })}`),
  post: (id: number) => request<PostDetail>(`/pub/v1/posts/${id}`),

  // Member auth
  login: (email: string, password: string) =>
    request<MemberAuthResponse>("/pub/v1/auth/login", { method: "POST", body: { email, password } }),
  me: (token: string) => request<MemberInfo>("/pub/v1/auth/me", { token }),
};
