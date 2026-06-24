"use client";

import { useState } from "react";
import { useSession } from "next-auth/react";
import { useQuery } from "@tanstack/react-query";
import { Check, Inbox, Loader2, Pencil, Plus, Sparkles, Trash2, X } from "lucide-react";

import {
  chatKnowledgeCreate,
  chatKnowledgeDelete,
  chatKnowledgeList,
  chatKnowledgeUpdate,
  chatUnansweredList,
  chatUnansweredResolve,
  type ChatKnowledgeDto,
  type ChatUnansweredDto,
} from "@/apis/chat-knowledge";
import { canWrite } from "@/lib/auth-utils";
import { SUCCESS_CODE } from "@/types/api";

const FIELD =
  "w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

type ModalState = { mode: "closed" } | { mode: "create" } | { mode: "edit"; item: ChatKnowledgeDto };

const emptyForm: ChatKnowledgeDto = {
  question: "",
  keywords: "",
  answer: "",
  enabled: true,
  sortOrder: 0,
};

export default function ChatKnowledgePage() {
  const { data: session } = useSession();
  const writable = canWrite(session?.user?.role);

  const [modal, setModal] = useState<ModalState>({ mode: "closed" });
  const [form, setForm] = useState<ChatKnowledgeDto>(emptyForm);
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  // When the create modal was opened from an unanswered question, resolve it on successful save.
  const [pendingUnansweredId, setPendingUnansweredId] = useState<number | null>(null);

  const listQuery = useQuery({
    queryKey: ["chat-knowledge"],
    queryFn: ({ signal }) => chatKnowledgeList(signal),
  });
  const items: ChatKnowledgeDto[] = listQuery.data?.resultObject ?? [];

  const unansweredQuery = useQuery({
    queryKey: ["chat-unanswered"],
    queryFn: ({ signal }) => chatUnansweredList(signal),
  });
  const unanswered: ChatUnansweredDto[] = unansweredQuery.data?.resultObject ?? [];

  const openCreate = () => {
    setForm(emptyForm);
    setPendingUnansweredId(null);
    setMessage(null);
    setModal({ mode: "create" });
  };

  /** Open the create modal prefilled from an unanswered question (one-click "teach"). */
  const teachFromUnanswered = (item: ChatUnansweredDto) => {
    setForm({ ...emptyForm, question: item.question.slice(0, 200), keywords: item.question.slice(0, 80) });
    setPendingUnansweredId(item.id);
    setMessage(null);
    setModal({ mode: "create" });
  };

  const dismissUnanswered = async (id: number) => {
    try {
      await chatUnansweredResolve(id);
      unansweredQuery.refetch();
    } catch {
      setMessage("처리 중 오류가 발생했습니다.");
    }
  };

  const openEdit = (item: ChatKnowledgeDto) => {
    setForm({ ...item });
    setMessage(null);
    setModal({ mode: "edit", item });
  };

  const closeModal = () => setModal({ mode: "closed" });

  const update = <K extends keyof ChatKnowledgeDto>(key: K, value: ChatKnowledgeDto[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setMessage(null);
    if (!form.question.trim() || !form.keywords.trim() || !form.answer.trim()) {
      setMessage("질문 · 키워드 · 답변을 모두 입력해 주세요.");
      return;
    }
    const payload: ChatKnowledgeDto = {
      question: form.question.trim(),
      keywords: form.keywords.trim(),
      answer: form.answer.trim(),
      enabled: form.enabled,
      sortOrder: Number.isFinite(form.sortOrder) ? form.sortOrder : 0,
    };
    setSaving(true);
    try {
      const res =
        modal.mode === "edit" && modal.item.id != null
          ? await chatKnowledgeUpdate(modal.item.id, payload)
          : await chatKnowledgeCreate(payload);
      if (res.resultCode === SUCCESS_CODE) {
        // Taught from an unanswered question → mark it resolved (learning loop closed).
        if (pendingUnansweredId != null) {
          await chatUnansweredResolve(pendingUnansweredId).catch(() => undefined);
          setPendingUnansweredId(null);
          unansweredQuery.refetch();
        }
        closeModal();
        setMessage("저장되었습니다.");
        listQuery.refetch();
      } else {
        setMessage(res.resultMessage ?? "저장에 실패했습니다.");
      }
    } catch {
      setMessage("저장 중 오류가 발생했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (item: ChatKnowledgeDto) => {
    if (item.id == null || !window.confirm(`'${item.question}' 지식을 삭제하시겠습니까?`)) {
      return;
    }
    setMessage(null);
    try {
      const res = await chatKnowledgeDelete(item.id);
      if (res.resultCode === SUCCESS_CODE) {
        setMessage("삭제되었습니다.");
        listQuery.refetch();
      } else {
        setMessage(res.resultMessage ?? "삭제에 실패했습니다.");
      }
    } catch {
      setMessage("삭제 중 오류가 발생했습니다.");
    }
  };

  return (
    <div>
      <div className="mb-4 flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">챗봇 지식관리</h1>
          <p className="mt-1 text-sm text-slate-500">
            챗봇이 답변에 사용하는 지식(FAQ)을 등록·학습시킵니다. 키워드가 사용자 질문에 포함되면 해당
            답변을 우선 사용하고, LLM 모드에서는 이 지식이 프롬프트에 함께 주입됩니다.
          </p>
        </div>
        {writable && (
          <button
            type="button"
            onClick={openCreate}
            className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
          >
            <Plus className="h-4 w-4" />
            지식 등록
          </button>
        )}
      </div>

      {/* Learning queue (A): questions the bot couldn't answer → one-click teach */}
      {unanswered.length > 0 && (
        <div className="mb-5 rounded-md border border-amber-200 bg-amber-50/60 p-4">
          <div className="mb-1 flex items-center gap-1.5 text-sm font-semibold text-amber-800">
            <Inbox className="h-4 w-4" /> 미답변 질문 (학습 대기) · {unanswered.length}
          </div>
          <p className="mb-3 text-xs text-amber-700/80">
            챗봇이 답하지 못한 질문입니다. ‘지식 등록’으로 답변을 추가하면 다음부터 챗봇이 답합니다.
          </p>
          <ul className="space-y-1.5">
            {unanswered.map((u) => (
              <li
                key={u.id}
                className="flex items-center justify-between gap-2 rounded-md border border-amber-200 bg-white px-3 py-2 text-sm"
              >
                <span className="truncate text-slate-800">{u.question}</span>
                {writable && (
                  <div className="flex shrink-0 items-center gap-1">
                    <button
                      type="button"
                      onClick={() => teachFromUnanswered(u)}
                      className="inline-flex items-center gap-1 rounded-md bg-brand px-2.5 py-1 text-xs font-medium text-white transition hover:bg-brand-dark"
                    >
                      <Sparkles className="h-3.5 w-3.5" /> 지식 등록
                    </button>
                    <button
                      type="button"
                      onClick={() => void dismissUnanswered(u.id)}
                      title="처리완료(무시)"
                      className="rounded-md p-1.5 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
                    >
                      <Check className="h-4 w-4" />
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="mb-3 flex items-center gap-3">
        <span className="text-sm text-slate-600">총 {items.length.toLocaleString()}건</span>
        {message && (
          <span className="rounded-md bg-blue-50 px-2.5 py-1 text-xs text-blue-700">{message}</span>
        )}
      </div>

      {listQuery.isLoading ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : listQuery.isError ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">지식 목록을 불러오지 못했습니다.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-md border border-slate-200 bg-white">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
              <tr>
                <th className="px-4 py-3">질문</th>
                <th className="px-4 py-3">키워드</th>
                <th className="px-4 py-3">답변</th>
                <th className="px-4 py-3">정렬</th>
                <th className="px-4 py-3">노출</th>
                <th className="px-4 py-3 text-right">관리</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-10 text-center text-slate-400">
                    등록된 지식이 없습니다.
                  </td>
                </tr>
              ) : (
                items.map((item) => (
                  <tr key={item.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-900">{item.question}</td>
                    <td className="px-4 py-3 text-xs text-slate-500">{item.keywords}</td>
                    <td className="max-w-md px-4 py-3 text-xs text-slate-600">
                      <span className="line-clamp-2">{item.answer}</span>
                    </td>
                    <td className="px-4 py-3 tabular-nums text-slate-700">{item.sortOrder}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                          item.enabled
                            ? "bg-emerald-100 text-emerald-700"
                            : "bg-slate-200 text-slate-600"
                        }`}
                      >
                        {item.enabled ? "사용" : "미사용"}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1">
                        <button
                          type="button"
                          onClick={() => openEdit(item)}
                          className="rounded p-1.5 text-slate-500 transition hover:bg-slate-100 hover:text-brand"
                          aria-label="수정"
                        >
                          <Pencil className="h-4 w-4" />
                        </button>
                        {writable && (
                          <button
                            type="button"
                            onClick={() => void handleDelete(item)}
                            className="rounded p-1.5 text-slate-500 transition hover:bg-red-50 hover:text-red-600"
                            aria-label="삭제"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {modal.mode !== "closed" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-lg rounded-md bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
              <h2 className="text-base font-semibold text-slate-900">
                {modal.mode === "edit" ? "지식 수정" : "지식 등록"}
              </h2>
              <button
                type="button"
                onClick={closeModal}
                className="rounded p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
                aria-label="닫기"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-4 px-5 py-4" noValidate>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-500">질문 / 주제 *</label>
                <input
                  className={FIELD}
                  value={form.question}
                  onChange={(e) => update("question", e.target.value)}
                  placeholder="예) 배송비"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-500">
                  키워드 * (공백/쉼표로 구분, 질문에 포함되면 매칭)
                </label>
                <input
                  className={FIELD}
                  value={form.keywords}
                  onChange={(e) => update("keywords", e.target.value)}
                  placeholder="예) 배송비 배송료"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-slate-500">답변 *</label>
                <textarea
                  className={FIELD}
                  rows={4}
                  value={form.answer}
                  onChange={(e) => update("answer", e.target.value)}
                  placeholder="챗봇이 사용자에게 줄 답변"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-500">정렬 순서</label>
                  <input
                    type="number"
                    className={FIELD}
                    value={form.sortOrder}
                    onChange={(e) => update("sortOrder", Number(e.target.value))}
                  />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-slate-500">사용 여부</label>
                  <select
                    className={FIELD}
                    value={form.enabled ? "Y" : "N"}
                    onChange={(e) => update("enabled", e.target.value === "Y")}
                  >
                    <option value="Y">사용</option>
                    <option value="N">미사용</option>
                  </select>
                </div>
              </div>
              {message && <p className="text-sm text-red-600">{message}</p>}
              <div className="flex justify-end gap-2 pt-1">
                <button
                  type="button"
                  onClick={closeModal}
                  disabled={saving}
                  className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:opacity-60"
                >
                  취소
                </button>
                <button
                  type="submit"
                  disabled={saving || !writable}
                  className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {saving && <Loader2 className="h-4 w-4 animate-spin" />}
                  저장
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
