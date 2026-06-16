import { axiosInstance } from "@/apis/custom-instance";
import type {
  ResultDTOUploadResponse,
  UploadResponse,
} from "@/apis/query/streamHubAdminAPI.schemas";

/**
 * uploadFile sends a single file to the content upload endpoint as multipart
 * form data and returns the storage key/url.
 *
 * The generated `useUpload` hook is intentionally bypassed: it serializes the
 * multipart body as JSON, which the backend rejects. Here we build a real
 * FormData so axios 1.x sets the multipart boundary itself, and the request
 * interceptor on `axiosInstance` attaches the bearer token automatically.
 */
export async function uploadFile(file: File): Promise<UploadResponse> {
  const form = new FormData();
  form.append("file", file);

  const res = await axiosInstance.post<ResultDTOUploadResponse>(
    "/v1/content/upload",
    form,
    {
      headers: { "Content-Type": "multipart/form-data" },
    },
  );

  return res.data.resultObject ?? {};
}
