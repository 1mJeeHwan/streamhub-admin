/**
 * Hand-written client for chatbot knowledge (FAQ) management (/v1/chat-admin/knowledge). Ships after
 * the current Orval spec, so it's issued directly through `customInstance` (same auth/refresh as
 * generated hooks). Replaceable by `npm run gen` post-deploy.
 */
import { customInstance } from "./custom-instance";
import type { ApiResponse } from "@/types/api";

/** A chatbot knowledge entry (mirrors org.streamhub.api.v1.chat.admin.dto.ChatKnowledgeDto). */
export interface ChatKnowledgeDto {
  id?: number;
  /** Short question/topic shown in the list (e.g. "배송비"). */
  question: string;
  /** Space/comma-separated trigger terms matched against the user message. */
  keywords: string;
  answer: string;
  enabled: boolean;
  sortOrder: number;
  updatedAt?: string | null;
}

export const chatKnowledgeList = (signal?: AbortSignal) =>
  customInstance<ApiResponse<ChatKnowledgeDto[]>>({
    url: "/v1/chat-admin/knowledge",
    method: "GET",
    signal,
  });

export const chatKnowledgeCreate = (data: ChatKnowledgeDto) =>
  customInstance<ApiResponse<ChatKnowledgeDto>>({
    url: "/v1/chat-admin/knowledge",
    method: "POST",
    data,
  });

export const chatKnowledgeUpdate = (id: number, data: ChatKnowledgeDto) =>
  customInstance<ApiResponse<ChatKnowledgeDto>>({
    url: `/v1/chat-admin/knowledge/${id}`,
    method: "PUT",
    data,
  });

export const chatKnowledgeDelete = (id: number) =>
  customInstance<ApiResponse<void>>({
    url: `/v1/chat-admin/knowledge/${id}`,
    method: "DELETE",
  });

/** An unanswered (FALLBACK) user question in the learning queue. */
export interface ChatUnansweredDto {
  id: number;
  question: string;
  sessionKey: string | null;
  createdAt: string;
}

export const chatUnansweredList = (signal?: AbortSignal) =>
  customInstance<ApiResponse<ChatUnansweredDto[]>>({
    url: "/v1/chat-admin/unanswered",
    method: "GET",
    signal,
  });

export const chatUnansweredResolve = (id: number) =>
  customInstance<ApiResponse<void>>({
    url: `/v1/chat-admin/unanswered/${id}/resolve`,
    method: "PUT",
  });
