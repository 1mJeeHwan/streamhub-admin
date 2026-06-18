"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Loader2, X } from "lucide-react";

import {
  usePostCommunityPostCreate,
  usePostCommunityPostUpdate,
} from "@/apis/query/post/post";
import {
  type BoardDto,
  type CommunityPostDto,
  type CommunityPostSaveRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { SUCCESS_CODE } from "@/types/api";

const FIELD_CLASS =
  "w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

interface CommunityPostFormDialogProps {
  /** Post being edited, or null/undefined when creating a new one. */
  post?: CommunityPostDto | null;
  /** Boards used to populate the board select. */
  boards: BoardDto[];
  onClose: () => void;
  /** Called after a successful create/update so the parent can refetch. */
  onSaved: () => void;
}

interface CommunityPostFormValues {
  boardId: string;
  title: string;
  category: string;
  writerName: string;
  secretYn: string;
  content: string;
}

const buildDefaults = (
  post?: CommunityPostDto | null,
): CommunityPostFormValues => ({
  boardId: post?.boardId != null ? String(post.boardId) : "",
  title: post?.title ?? "",
  category: post?.category ?? "",
  writerName: post?.writerName ?? "",
  secretYn: post?.secretYn ?? "N",
  content: post?.content ?? "",
});

const buildPayload = (
  values: CommunityPostFormValues,
): CommunityPostSaveRequest => {
  const trimmedCategory = values.category.trim();
  const trimmedWriter = values.writerName.trim();
  const trimmedContent = values.content.trim();

  return {
    boardId: Number(values.boardId),
    title: values.title.trim(),
    category: trimmedCategory ? trimmedCategory : undefined,
    writerName: trimmedWriter ? trimmedWriter : undefined,
    content: trimmedContent ? trimmedContent : undefined,
    secretYn: values.secretYn,
  };
};

/**
 * CommunityPostFormDialog is a modal create/edit form for a community post.
 * When `post` has an id it issues an update; otherwise it creates a new post.
 * The board select is populated from the boards passed by the parent page.
 */
export default function CommunityPostFormDialog({
  post,
  boards,
  onClose,
  onSaved,
}: CommunityPostFormDialogProps) {
  const isEdit = post?.id != null;
  const [message, setMessage] = useState<string | null>(null);

  const createMutation = usePostCommunityPostCreate();
  const updateMutation = usePostCommunityPostUpdate();
  const isPending = createMutation.isPending || updateMutation.isPending;

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CommunityPostFormValues>({
    defaultValues: buildDefaults(post),
  });

  useEffect(() => {
    reset(buildDefaults(post));
  }, [post, reset]);

  const handleResult = (resultCode?: string, resultMessage?: string) => {
    if (resultCode === SUCCESS_CODE) {
      onSaved();
    } else {
      setMessage(resultMessage ?? "저장에 실패했습니다.");
    }
  };

  const onSubmit = (values: CommunityPostFormValues) => {
    setMessage(null);
    const payload = buildPayload(values);

    if (isEdit && post?.id != null) {
      updateMutation.mutate(
        { id: post.id, data: payload },
        {
          onSuccess: (response) =>
            handleResult(response.resultCode, response.resultMessage),
          onError: () => setMessage("저장 중 오류가 발생했습니다."),
        },
      );
    } else {
      createMutation.mutate(
        { data: payload },
        {
          onSuccess: (response) =>
            handleResult(response.resultCode, response.resultMessage),
          onError: () => setMessage("저장 중 오류가 발생했습니다."),
        },
      );
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="flex max-h-[90vh] w-full max-w-lg flex-col rounded-md bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
          <h2 className="text-base font-semibold text-slate-900">
            {isEdit ? "게시글 수정" : "글 작성"}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
            aria-label="닫기"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form
          onSubmit={handleSubmit(onSubmit)}
          className="flex-1 overflow-y-auto px-5 py-4"
          noValidate
        >
          {message && (
            <p className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
              {message}
            </p>
          )}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {/* Board */}
            <div>
              <label
                htmlFor="post-board"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                게시판 *
              </label>
              <select
                id="post-board"
                className={FIELD_CLASS}
                {...register("boardId", { required: "게시판을 선택하세요." })}
              >
                <option value="">선택</option>
                {boards.map((board) => (
                  <option key={board.id} value={String(board.id)}>
                    {board.name ?? "-"}
                  </option>
                ))}
              </select>
              {errors.boardId && (
                <p className="mt-1 text-xs text-red-600">
                  {errors.boardId.message}
                </p>
              )}
            </div>

            {/* Category */}
            <div>
              <label
                htmlFor="post-category"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                카테고리
              </label>
              <input
                id="post-category"
                type="text"
                maxLength={40}
                placeholder="선택"
                className={FIELD_CLASS}
                {...register("category")}
              />
            </div>

            {/* Title */}
            <div className="sm:col-span-2">
              <label
                htmlFor="post-title"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                제목 *
              </label>
              <input
                id="post-title"
                type="text"
                maxLength={200}
                className={FIELD_CLASS}
                {...register("title", { required: "제목을 입력하세요." })}
              />
              {errors.title && (
                <p className="mt-1 text-xs text-red-600">
                  {errors.title.message}
                </p>
              )}
            </div>

            {/* Writer name */}
            <div>
              <label
                htmlFor="post-writer"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                작성자
              </label>
              <input
                id="post-writer"
                type="text"
                maxLength={60}
                className={FIELD_CLASS}
                {...register("writerName")}
              />
            </div>

            {/* Secret yn */}
            <div>
              <label
                htmlFor="post-secret"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                비밀글 여부
              </label>
              <select
                id="post-secret"
                className={FIELD_CLASS}
                {...register("secretYn")}
              >
                <option value="N">공개</option>
                <option value="Y">비밀글</option>
              </select>
            </div>

            {/* Content */}
            <div className="sm:col-span-2">
              <label
                htmlFor="post-content"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                내용
              </label>
              <textarea
                id="post-content"
                rows={8}
                maxLength={2000}
                className={`${FIELD_CLASS} resize-y`}
                {...register("content")}
              />
            </div>
          </div>

          <div className="mt-5 flex justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              disabled={isPending}
              className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:opacity-60"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={isPending}
              className="flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              저장
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
