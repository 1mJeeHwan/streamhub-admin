/**
 * Hand-written client for chatbot analytics (/v1/chat-admin/stats). Ships after the current Orval
 * spec, so it's issued directly through `customInstance`. Replaceable by `npm run gen` post-deploy.
 */
import { customInstance } from "./custom-instance";
import type { ApiResponse } from "@/types/api";

export interface ChatCountItem {
  label: string;
  count: number;
}

export interface ChatDailyItem {
  date: string;
  count: number;
}

/** Chatbot analytics snapshot (mirrors org.streamhub.api.v1.chat.admin.dto.ChatStatsDto). */
export interface ChatStatsDto {
  totalSessions: number;
  userQuestions: number;
  botReplies: number;
  fallbackCount: number;
  fallbackRate: number;
  openUnanswered: number;
  intents: ChatCountItem[];
  providers: ChatCountItem[];
  daily: ChatDailyItem[];
}

export const chatStats = (signal?: AbortSignal) =>
  customInstance<ApiResponse<ChatStatsDto>>({
    url: "/v1/chat-admin/stats",
    method: "GET",
    signal,
  });
