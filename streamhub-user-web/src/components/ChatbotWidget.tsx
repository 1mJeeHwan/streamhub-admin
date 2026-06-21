"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { MessageCircle, X, Send, Sparkles } from "lucide-react";
import clsx from "clsx";
import {
  sendChat,
  answerLocally,
  greeting,
  INITIAL_QUICK_REPLIES,
  type ChatMessage,
} from "@/lib/chat";
import { useAuth } from "@/lib/auth";
import { useAudioPlayer } from "./player/AudioPlayerProvider";

/** localStorage key for the front-generated chat session id (UUID). */
const SESSION_STORAGE_KEY = "streamhub.chat.sessionKey";

/** localStorage key for the user-dragged launcher position ({side, bottom}). */
const POS_STORAGE_KEY = "streamhub.chat.launcherPos";

/** Reads (or lazily creates) the per-browser session key kept in localStorage. */
function getSessionKey(): string {
  if (typeof window === "undefined") return "";
  let key = window.localStorage.getItem(SESSION_STORAGE_KEY);
  if (!key) {
    key = crypto.randomUUID().slice(0, 40);
    window.localStorage.setItem(SESSION_STORAGE_KEY, key);
  }
  return key;
}

let counter = 0;
function nextId(): string {
  counter += 1;
  return `m${counter}`;
}

/**
 * Floating chatbot widget (C5). Renders a bottom-right launcher inside the phone frame; opening
 * it reveals a chat panel with message bubbles, quick-reply chips and a text input. Rule-based
 * demo answers come from `sendChat`, which calls the backend when available and otherwise falls
 * back to a local mock. Mounted globally in the root layout — does not touch shared nav.
 */
export function ChatbotWidget() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([greeting()]);
  const [quickReplies, setQuickReplies] = useState<string[]>(INITIAL_QUICK_REPLIES);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  // Whether the backend answered with the real LLM (testMode=false). null until the first
  // backend-backed reply arrives; client-side mock answers don't change it.
  const [llmActive, setLlmActive] = useState<boolean | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  // Lift the launcher above the mini player when a track is loaded, so they never overlap.
  const { current: previewTrack } = useAudioPlayer();
  // Member token (when logged in) lets the bot check the user's own points/coupons/orders/etc.
  const { token } = useAuth();

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, open]);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  const submit = useCallback(
    async (raw: string) => {
      const text = raw.trim();
      if (!text || sending) return;

      setInput("");
      setQuickReplies([]);
      setMessages((prev) => [...prev, { id: nextId(), role: "USER", content: text }]);
      setSending(true);

      // Feature guidance + (auth-aware) "check my …" answers are resolved client-side first;
      // anything else falls through to the backend/mock (FAQ · order lookup · product).
      const reply = (await answerLocally(text, token)) ?? (await sendChat(getSessionKey(), text));

      setMessages((prev) => [
        ...prev,
        { id: nextId(), role: "BOT", content: reply.text, intent: reply.intent },
      ]);
      setQuickReplies(reply.quickReplies ?? []);
      // Only a real backend reply tells us the provider; client mock answers (mocked) don't.
      if (!reply.mocked) setLlmActive(!reply.testMode);
      setSending(false);
    },
    [sending, token],
  );

  // --- Draggable launcher -------------------------------------------------
  // Long-press (or drag past a small threshold) to pick the launcher up, then move it
  // up/down freely; on release it snaps to the nearer side (left/right) and the chosen
  // {side, bottom} is persisted to localStorage. A plain tap still opens the chat.
  const columnRef = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<{ side: "left" | "right"; bottom: number }>({
    side: "right",
    bottom: 100,
  });
  const [dragging, setDragging] = useState(false);
  const gesture = useRef<{
    startX: number;
    startY: number;
    dragging: boolean;
    timer: ReturnType<typeof setTimeout> | null;
  } | null>(null);

  useEffect(() => {
    try {
      const raw = window.localStorage.getItem(POS_STORAGE_KEY);
      if (raw) {
        const p = JSON.parse(raw) as { side?: string; bottom?: number };
        if ((p.side === "left" || p.side === "right") && typeof p.bottom === "number") {
          setPos({ side: p.side, bottom: p.bottom });
        }
      }
    } catch {
      /* ignore malformed/blocked storage */
    }
  }, []);

  // Keep the launcher clear of the mini player (when a track is loaded) and the tab bar.
  const minBottom = previewTrack ? 168 : 84;
  const clampBottom = useCallback(
    (b: number) => {
      const vh = typeof window !== "undefined" ? window.innerHeight : 800;
      return Math.min(Math.max(b, minBottom), vh - 120);
    },
    [minBottom],
  );

  const beginDrag = useCallback(() => {
    if (gesture.current) gesture.current.dragging = true;
    setDragging(true);
  }, []);

  const onPointerDown = (e: React.PointerEvent) => {
    e.currentTarget.setPointerCapture?.(e.pointerId);
    const timer = setTimeout(beginDrag, 220);
    gesture.current = { startX: e.clientX, startY: e.clientY, dragging: false, timer };
  };

  const onPointerMove = (e: React.PointerEvent) => {
    const g = gesture.current;
    if (!g) return;
    if (!g.dragging && Math.hypot(e.clientX - g.startX, e.clientY - g.startY) > 8) {
      if (g.timer) clearTimeout(g.timer);
      beginDrag();
    }
    if (!g.dragging) return;
    const rect = columnRef.current?.getBoundingClientRect();
    const centerX = rect ? rect.left + rect.width / 2 : window.innerWidth / 2;
    setPos({
      side: e.clientX < centerX ? "left" : "right",
      bottom: clampBottom(window.innerHeight - e.clientY - 28),
    });
  };

  const endGesture = () => {
    const g = gesture.current;
    gesture.current = null;
    if (!g) return;
    if (g.timer) clearTimeout(g.timer);
    if (g.dragging) {
      setDragging(false);
      setPos((p) => {
        const next = { side: p.side, bottom: clampBottom(p.bottom) };
        try {
          window.localStorage.setItem(POS_STORAGE_KEY, JSON.stringify(next));
        } catch {
          /* ignore blocked storage */
        }
        return next;
      });
    } else {
      setOpen(true); // plain tap → open chat
    }
  };

  return (
    <>
      {/* Launcher — anchored to the phone frame (480px column), not the viewport edge.
          Draggable: long-press / drag to move vertically, snaps to either side. */}
      <div className="pointer-events-none fixed inset-x-0 bottom-0 z-40">
        <div ref={columnRef} className="relative mx-auto h-0 w-full max-w-[480px]">
          {!open && (
            <button
              type="button"
              aria-label="챗봇 열기 (길게 눌러 위치 이동)"
              onPointerDown={onPointerDown}
              onPointerMove={onPointerMove}
              onPointerUp={endGesture}
              onPointerCancel={endGesture}
              style={
                {
                  [pos.side]: 16,
                  bottom: clampBottom(pos.bottom),
                  touchAction: "none",
                } as React.CSSProperties
              }
              className={clsx(
                "pointer-events-auto absolute grid h-14 w-14 place-items-center rounded-full bg-primary text-white shadow-lg shadow-primary/30 select-none",
                dragging
                  ? "scale-110 cursor-grabbing ring-4 ring-primary/30"
                  : "transition-all active:scale-95",
              )}
            >
              <MessageCircle className="pointer-events-none h-6 w-6" />
              <span className="pointer-events-none absolute -right-1 -top-1 flex h-5 items-center rounded-full bg-point px-1.5 text-[9px] font-bold text-white">
                AI
              </span>
            </button>
          )}
        </div>
      </div>

      {/* Chat panel */}
      {open && (
        <div className="pointer-events-none fixed inset-x-0 bottom-0 z-50">
          <div className="relative mx-auto w-full max-w-[480px]">
            <div className="pointer-events-auto absolute bottom-[88px] right-3 left-3 flex h-[70dvh] max-h-[560px] flex-col overflow-hidden rounded-2xl border border-border bg-bg shadow-2xl animate-fade-up">
              {/* Header */}
              <div className="flex items-start justify-between gap-2 border-b border-border bg-surface px-4 py-3">
                <div className="flex items-start gap-2.5">
                  <div className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-primary/15">
                    <Sparkles className="h-4.5 w-4.5 text-primary" />
                  </div>
                  <div>
                    <div className="flex items-center gap-1.5">
                      <h2 className="text-sm font-bold text-active">StreamHub 도우미</h2>
                      <span className="rounded bg-primary/15 px-1.5 py-0.5 text-[10px] font-bold text-primary">
                        {llmActive ? "AI" : "AI 데모"}
                      </span>
                    </div>
                    <p className="mt-0.5 text-[11px] leading-tight text-inactive">
                      {llmActive === null
                        ? "AI 챗봇 · 기능 안내·주문/상품 조회"
                        : llmActive
                          ? "AI 챗봇 · 자연어 응답(Gemini)"
                          : "데모 챗봇 · 룰 기반"}
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  aria-label="챗봇 닫기"
                  onClick={() => setOpen(false)}
                  className="rounded-lg p-1.5 text-inactive transition active:bg-card"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              {/* Messages */}
              <div ref={scrollRef} className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
                {messages.map((m) => (
                  <Bubble key={m.id} message={m} />
                ))}
                {sending && <TypingBubble />}
              </div>

              {/* Quick replies */}
              {quickReplies.length > 0 && (
                <div className="hrow border-t border-border px-4 py-2.5">
                  {quickReplies.map((q) => (
                    <button
                      key={q}
                      type="button"
                      onClick={() => submit(q)}
                      disabled={sending}
                      className="pill text-xs disabled:opacity-50"
                    >
                      {q}
                    </button>
                  ))}
                </div>
              )}

              {/* Input */}
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  submit(input);
                }}
                className="flex items-center gap-2 border-t border-border bg-surface px-3 py-2.5"
              >
                <input
                  ref={inputRef}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  placeholder="메시지를 입력하세요…"
                  maxLength={2000}
                  className="min-w-0 flex-1 rounded-xl border border-border bg-bg px-3.5 py-2.5 text-sm text-active outline-none transition placeholder:text-inactive focus:border-primary"
                />
                <button
                  type="submit"
                  aria-label="전송"
                  disabled={sending || input.trim().length === 0}
                  className={clsx(
                    "grid h-10 w-10 shrink-0 place-items-center rounded-xl text-white transition active:scale-95",
                    input.trim().length === 0 || sending
                      ? "bg-border text-inactive"
                      : "bg-primary",
                  )}
                >
                  <Send className="h-4.5 w-4.5" />
                </button>
              </form>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

function Bubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === "USER";
  return (
    <div className={clsx("flex", isUser ? "justify-end" : "justify-start")}>
      <div
        className={clsx(
          "max-w-[82%] whitespace-pre-wrap rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed",
          isUser
            ? "rounded-br-md bg-primary text-white"
            : "rounded-bl-md border border-border bg-card text-active",
        )}
      >
        {message.content}
      </div>
    </div>
  );
}

function TypingBubble() {
  return (
    <div className="flex justify-start">
      <div className="flex items-center gap-1 rounded-2xl rounded-bl-md border border-border bg-card px-4 py-3">
        <Dot delay="0ms" />
        <Dot delay="150ms" />
        <Dot delay="300ms" />
      </div>
    </div>
  );
}

function Dot({ delay }: { delay: string }) {
  return (
    <span
      className="h-1.5 w-1.5 animate-bounce rounded-full bg-inactive"
      style={{ animationDelay: delay }}
    />
  );
}
