import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "../api";

function mockFetch(impl: () => Promise<unknown>) {
  vi.stubGlobal("fetch", vi.fn(impl as () => Promise<Response>));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("api request wrapper", () => {
  it("unwraps resultObject on a 0000 success envelope", async () => {
    mockFetch(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ resultCode: "0000", resultMessage: "성공", resultObject: { id: 1, title: "t" } }),
    }));

    await expect(api.content(1)).resolves.toEqual({ id: 1, title: "t" });
  });

  it("throws ApiError carrying the HTTP status on an error response", async () => {
    mockFetch(async () => ({
      ok: false,
      status: 404,
      json: async () => ({ resultCode: "4040", resultMessage: "대상을 찾을 수 없습니다" }),
    }));

    await expect(api.content(999)).rejects.toMatchObject({ name: "ApiError", status: 404 });
  });

  it("throws when resultCode is not 0000 even on HTTP 200", async () => {
    mockFetch(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ resultCode: "5000", resultMessage: "서버 오류" }),
    }));

    await expect(api.posts()).rejects.toBeInstanceOf(ApiError);
  });

  it("wraps network failures as ApiError(status 0)", async () => {
    mockFetch(async () => {
      throw new TypeError("network down");
    });

    await expect(api.home()).rejects.toMatchObject({ name: "ApiError", status: 0 });
  });

  it("sends the bearer token on authenticated calls (me)", async () => {
    const fetchSpy = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ resultCode: "0000", resultObject: { id: 9, name: "김민준" } }),
    }));
    vi.stubGlobal("fetch", fetchSpy as unknown as typeof fetch);

    await api.me("member.jwt");

    const [, init] = fetchSpy.mock.calls[0] as unknown as [string, RequestInit];
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer member.jwt");
  });
});
