"use client";

import { useMemo, useState } from "react";
import { Eye, Loader2, Lock, Pencil, Plus, Trash2, X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";

import { boardList } from "@/apis/query/board/board";
import {
  postCommunityPostDetail,
  postCommunityPostList,
  usePostCommunityPostDelete,
} from "@/apis/query/post/post";
import {
  type BoardDto,
  type CommunityPostDto,
  type CommunityPostSearchRequest,
} from "@/apis/query/streamHubAdminAPI.schemas";
import CommunityPostFormDialog from "@/components/posts/CommunityPostFormDialog";
import { SUCCESS_CODE } from "@/types/api";

const ALL_BOARDS = "";

export default function PostsPage() {
  // Draft filter values (form) vs. applied filter (query input)
  const [boardId, setBoardId] = useState<string>(ALL_BOARDS);
  const [keyword, setKeyword] = useState("");
  const [search, setSearch] = useState<CommunityPostSearchRequest>({});
  const [detailId, setDetailId] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  // null = closed; { post: null } = create; { post } = edit a specific post
  const [formState, setFormState] = useState<{
    post: CommunityPostDto | null;
  } | null>(null);

  const boardsQuery = useQuery({
    queryKey: ["board-list"],
    queryFn: ({ signal }) => boardList(signal),
  });

  const listQuery = useQuery({
    queryKey: ["community-post-list", search],
    queryFn: ({ signal }) => postCommunityPostList(search, signal),
  });

  const deleteMutation = usePostCommunityPostDelete();

  const boards: BoardDto[] = boardsQuery.data?.resultObject ?? [];
  const posts: CommunityPostDto[] = listQuery.data?.resultObject ?? [];

  const boardNameById = useMemo(() => {
    const map = new Map<number, string>();
    for (const board of boards) {
      if (board.id != null) {
        map.set(board.id, board.name ?? "-");
      }
    }
    return map;
  }, [boards]);

  const handleSearch = () => {
    setMessage(null);
    const next: CommunityPostSearchRequest = {};
    if (boardId !== ALL_BOARDS) {
      next.boardId = Number(boardId);
    }
    const term = keyword.trim();
    if (term) {
      next.keyword = term;
    }
    setSearch(next);
  };

  const handleSaved = () => {
    setMessage("저장되었습니다.");
    setFormState(null);
    listQuery.refetch();
  };

  const handleDelete = (post: CommunityPostDto) => {
    if (post.id == null) {
      return;
    }
    if (
      !window.confirm(`'${post.title ?? "게시글"}'을(를) 삭제하시겠습니까?`)
    ) {
      return;
    }
    setMessage(null);
    deleteMutation.mutate(
      { id: post.id },
      {
        onSuccess: (response) => {
          if (response.resultCode === SUCCESS_CODE) {
            setMessage("삭제되었습니다.");
            listQuery.refetch();
          } else {
            setMessage(response.resultMessage ?? "삭제에 실패했습니다.");
          }
        },
        onError: () => setMessage("삭제 중 오류가 발생했습니다."),
      },
    );
  };

  return (
    <div>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">
            공지·나눔·기도제목
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            커뮤니티 게시글을 게시판/키워드로 검색하고 작성·수정·삭제합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            setMessage(null);
            setFormState({ post: null });
          }}
          className="flex shrink-0 items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
        >
          <Plus className="h-4 w-4" />
          글 작성
        </button>
      </div>

      {/* Search bar */}
      <div className="mb-4 flex flex-wrap items-end gap-3 rounded-md border border-slate-200 bg-white p-4">
        <div className="flex flex-col">
          <label
            htmlFor="post-board"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            게시판
          </label>
          <select
            id="post-board"
            value={boardId}
            onChange={(event) => setBoardId(event.target.value)}
            className="w-48 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          >
            <option value={ALL_BOARDS}>전체</option>
            {boards.map((board) => (
              <option key={board.id} value={String(board.id)}>
                {board.name ?? "-"}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col">
          <label
            htmlFor="post-keyword"
            className="mb-1 text-xs font-medium text-slate-600"
          >
            검색어
          </label>
          <input
            id="post-keyword"
            type="text"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                handleSearch();
              }
            }}
            placeholder="제목 / 내용 / 작성자"
            className="w-64 rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand"
          />
        </div>
        <button
          type="button"
          onClick={handleSearch}
          className="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
        >
          검색
        </button>
      </div>

      {/* Summary */}
      <div className="mb-3 flex items-center gap-3">
        <span className="text-sm text-slate-600">
          총 {posts.length.toLocaleString()}건
        </span>
        {message && (
          <span className="rounded-md bg-blue-50 px-2.5 py-1 text-xs text-blue-700">
            {message}
          </span>
        )}
      </div>

      {/* List */}
      {listQuery.isLoading ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
        </div>
      ) : listQuery.isError ? (
        <div className="flex h-64 items-center justify-center rounded-md border border-slate-200 bg-white">
          <p className="text-sm text-red-600">
            게시글 목록을 불러오지 못했습니다.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-md border border-slate-200 bg-white">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
              <tr>
                <th className="px-4 py-3">게시판</th>
                <th className="px-4 py-3">제목</th>
                <th className="px-4 py-3">작성자</th>
                <th className="px-4 py-3 text-right">추천</th>
                <th className="px-4 py-3 text-right">조회</th>
                <th className="px-4 py-3">작성일</th>
                <th className="px-4 py-3 text-right">관리</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {posts.length === 0 ? (
                <tr>
                  <td
                    colSpan={7}
                    className="px-4 py-10 text-center text-slate-400"
                  >
                    조회된 게시글이 없습니다.
                  </td>
                </tr>
              ) : (
                posts.map((post) => (
                  <tr key={post.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 text-slate-700">
                      {post.boardId != null
                        ? (boardNameById.get(post.boardId) ?? "-")
                        : "-"}
                    </td>
                    <td className="px-4 py-3 font-medium text-slate-900">
                      <span className="inline-flex items-center gap-1.5">
                        {post.secretYn === "Y" && (
                          <Lock className="h-3.5 w-3.5 text-slate-400" />
                        )}
                        {post.title ?? "-"}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-slate-700">
                      {post.writerName ?? "-"}
                    </td>
                    <td className="px-4 py-3 text-right text-slate-700">
                      {(post.recommendCount ?? 0).toLocaleString()}
                    </td>
                    <td className="px-4 py-3 text-right text-slate-700">
                      {(post.viewCount ?? 0).toLocaleString()}
                    </td>
                    <td className="px-4 py-3 text-slate-700">
                      {post.createdAt ?? "-"}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1">
                        <button
                          type="button"
                          onClick={() =>
                            post.id != null && setDetailId(post.id)
                          }
                          className="rounded p-1.5 text-slate-500 transition hover:bg-slate-100 hover:text-brand"
                          aria-label="상세"
                        >
                          <Eye className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setMessage(null);
                            setFormState({ post });
                          }}
                          className="rounded p-1.5 text-slate-500 transition hover:bg-slate-100 hover:text-brand"
                          aria-label="수정"
                        >
                          <Pencil className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDelete(post)}
                          disabled={deleteMutation.isPending}
                          className="rounded p-1.5 text-slate-500 transition hover:bg-red-50 hover:text-red-600 disabled:opacity-50"
                          aria-label="삭제"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {detailId != null && (
        <PostDetailDialog
          id={detailId}
          boardNameById={boardNameById}
          onClose={() => setDetailId(null)}
        />
      )}

      {formState != null && (
        <CommunityPostFormDialog
          post={formState.post}
          boards={boards}
          onClose={() => setFormState(null)}
          onSaved={handleSaved}
        />
      )}
    </div>
  );
}

interface PostDetailDialogProps {
  id: number;
  boardNameById: Map<number, string>;
  onClose: () => void;
}

function PostDetailDialog({
  id,
  boardNameById,
  onClose,
}: PostDetailDialogProps) {
  const detailQuery = useQuery({
    queryKey: ["community-post-detail", id],
    queryFn: ({ signal }) => postCommunityPostDetail(id, signal),
  });

  const post: CommunityPostDto | undefined = detailQuery.data?.resultObject;
  const boardName =
    post?.boardId != null ? (boardNameById.get(post.boardId) ?? "-") : "-";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="flex max-h-[85vh] w-full max-w-2xl flex-col overflow-hidden rounded-lg bg-white shadow-xl">
        <div className="flex items-start justify-between border-b border-slate-200 px-5 py-4">
          <div>
            <p className="text-xs text-slate-500">{boardName}</p>
            <h2 className="mt-0.5 flex items-center gap-1.5 text-lg font-semibold text-slate-900">
              {post?.secretYn === "Y" && (
                <Lock className="h-4 w-4 text-slate-400" />
              )}
              {detailQuery.isLoading ? "불러오는 중..." : (post?.title ?? "-")}
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded p-1.5 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
            aria-label="닫기"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4">
          {detailQuery.isLoading ? (
            <div className="flex h-40 items-center justify-center">
              <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
            </div>
          ) : detailQuery.isError || !post ? (
            <p className="text-sm text-red-600">
              게시글을 불러오지 못했습니다.
            </p>
          ) : (
            <>
              <div className="mb-4 flex flex-wrap gap-x-6 gap-y-1 text-xs text-slate-500">
                <span>작성자: {post.writerName ?? "-"}</span>
                <span>추천 {(post.recommendCount ?? 0).toLocaleString()}</span>
                <span>조회 {(post.viewCount ?? 0).toLocaleString()}</span>
                <span>{post.createdAt ?? "-"}</span>
              </div>
              <div className="whitespace-pre-wrap text-sm leading-relaxed text-slate-800">
                {post.content?.trim() ? post.content : "내용이 없습니다."}
              </div>
            </>
          )}
        </div>

        <div className="border-t border-slate-200 px-5 py-3 text-right">
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  );
}
