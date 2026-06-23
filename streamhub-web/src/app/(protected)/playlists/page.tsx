"use client";

import { useEffect, useState } from "react";
import { useSession } from "next-auth/react";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, ChevronUp, Loader2, Pencil, Plus, Trash2, X } from "lucide-react";

import {
  pickerAlbumTracks,
  pickerAlbums,
  playlistCreate,
  playlistDelete,
  playlistDetail,
  playlistList,
  playlistUpdate,
  type AlbumPick,
  type PlaylistDto,
  type TrackPick,
} from "@/apis/playlist";
import { canWrite } from "@/lib/auth-utils";
import { SUCCESS_CODE } from "@/types/api";

const FIELD =
  "rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

interface SelectedTrack {
  id: number;
  title: string;
  album: string;
}

function PlaylistDialog({
  editId,
  onClose,
  onSaved,
}: {
  editId: number | null;
  onClose: () => void;
  onSaved: (message: string) => void;
}) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [useYn, setUseYn] = useState("Y");
  const [sortOrder, setSortOrder] = useState("0");
  const [selected, setSelected] = useState<SelectedTrack[]>([]);
  const [albumId, setAlbumId] = useState<number | "">("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const albumsQuery = useQuery({
    queryKey: ["picker-albums"],
    queryFn: ({ signal }) => pickerAlbums(signal),
  });
  const albums: AlbumPick[] = albumsQuery.data?.resultObject?.contents ?? [];

  const tracksQuery = useQuery({
    queryKey: ["picker-tracks", albumId],
    queryFn: ({ signal }) => pickerAlbumTracks(albumId as number, signal),
    enabled: albumId !== "",
  });
  const albumTracks: TrackPick[] = tracksQuery.data?.resultObject?.tracks ?? [];
  const currentAlbumTitle = albums.find((a) => a.id === albumId)?.title ?? "";

  const detailQuery = useQuery({
    queryKey: ["playlist-detail", editId],
    queryFn: ({ signal }) => playlistDetail(editId as number, signal),
    enabled: editId != null,
  });

  useEffect(() => {
    const d = detailQuery.data?.resultObject;
    if (d) {
      setTitle(d.title);
      setDescription(d.description ?? "");
      setUseYn(d.useYn);
      setSortOrder(String(d.sortOrder));
      setSelected(
        d.tracks.map((t) => ({ id: t.id, title: t.title, album: t.albumTitle ?? "" })),
      );
    }
  }, [detailQuery.data]);

  const addTrack = (track: TrackPick) => {
    if (selected.some((s) => s.id === track.id)) {
      return;
    }
    setSelected((prev) => [...prev, { id: track.id, title: track.title, album: currentAlbumTitle }]);
  };

  const removeTrack = (id: number) => setSelected((prev) => prev.filter((s) => s.id !== id));

  const moveTrack = (index: number, dir: -1 | 1) => {
    const next = index + dir;
    if (next < 0 || next >= selected.length) return;
    setSelected((prev) => {
      const copy = [...prev];
      [copy[index], copy[next]] = [copy[next], copy[index]];
      return copy;
    });
  };

  const handleSave = async () => {
    if (!title.trim()) {
      setError("제목을 입력하세요.");
      return;
    }
    setError(null);
    setSaving(true);
    const payload = {
      title: title.trim(),
      description: description.trim() || null,
      sortOrder: Number(sortOrder) || 0,
      useYn,
      trackIds: selected.map((s) => s.id),
    };
    try {
      const res = editId != null ? await playlistUpdate(editId, payload) : await playlistCreate(payload);
      if (res.resultCode === SUCCESS_CODE) {
        onSaved(editId != null ? "수정되었습니다." : "생성되었습니다.");
      } else {
        setError(res.resultMessage ?? "저장에 실패했습니다.");
      }
    } catch {
      setError("저장 중 오류가 발생했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-lg bg-white">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
          <h2 className="text-base font-bold text-slate-900">
            {editId != null ? "플레이리스트 수정" : "플레이리스트 등록"}
          </h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="grid flex-1 grid-cols-1 gap-5 overflow-y-auto p-5 md:grid-cols-2">
          {/* Left: meta */}
          <div className="space-y-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-500">제목</label>
              <input value={title} onChange={(e) => setTitle(e.target.value)} className={`${FIELD} w-full`} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-500">설명</label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
                className={`${FIELD} w-full`}
              />
            </div>
            <div className="flex gap-2">
              <div className="flex-1">
                <label className="mb-1 block text-xs font-medium text-slate-500">정렬</label>
                <input
                  type="number"
                  value={sortOrder}
                  onChange={(e) => setSortOrder(e.target.value)}
                  className={`${FIELD} w-full`}
                />
              </div>
              <div className="flex-1">
                <label className="mb-1 block text-xs font-medium text-slate-500">노출</label>
                <select value={useYn} onChange={(e) => setUseYn(e.target.value)} className={`${FIELD} w-full`}>
                  <option value="Y">노출</option>
                  <option value="N">미노출</option>
                </select>
              </div>
            </div>

            {/* Selected tracks (ordered) */}
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-500">
                수록곡 ({selected.length})
              </label>
              <ul className="max-h-52 space-y-1 overflow-y-auto rounded-md border border-slate-200 p-2">
                {selected.length === 0 ? (
                  <li className="py-6 text-center text-xs text-slate-400">오른쪽에서 곡을 추가하세요.</li>
                ) : (
                  selected.map((s, i) => (
                    <li key={s.id} className="flex items-center gap-1 rounded bg-slate-50 px-2 py-1">
                      <span className="min-w-0 flex-1 truncate text-xs text-slate-700">
                        {i + 1}. {s.title} <span className="text-slate-400">· {s.album}</span>
                      </span>
                      <button onClick={() => moveTrack(i, -1)} className="p-0.5 text-slate-400 hover:text-slate-700">
                        <ChevronUp className="h-3.5 w-3.5" />
                      </button>
                      <button onClick={() => moveTrack(i, 1)} className="p-0.5 text-slate-400 hover:text-slate-700">
                        <ChevronDown className="h-3.5 w-3.5" />
                      </button>
                      <button onClick={() => removeTrack(s.id)} className="p-0.5 text-rose-500 hover:text-rose-700">
                        <X className="h-3.5 w-3.5" />
                      </button>
                    </li>
                  ))
                )}
              </ul>
            </div>
          </div>

          {/* Right: track picker */}
          <div className="space-y-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-500">앨범 선택</label>
              <select
                value={albumId}
                onChange={(e) => setAlbumId(e.target.value ? Number(e.target.value) : "")}
                className={`${FIELD} w-full`}
              >
                <option value="">앨범을 선택하세요</option>
                {albums.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.title} — {a.artist}
                  </option>
                ))}
              </select>
            </div>
            <div className="rounded-md border border-slate-200">
              {albumId === "" ? (
                <p className="py-10 text-center text-xs text-slate-400">앨범을 선택하면 수록곡이 표시됩니다.</p>
              ) : tracksQuery.isLoading ? (
                <div className="flex justify-center py-10 text-slate-400">
                  <Loader2 className="h-5 w-5 animate-spin" />
                </div>
              ) : (
                <ul className="max-h-72 divide-y divide-slate-100 overflow-y-auto">
                  {albumTracks.map((t) => {
                    const added = selected.some((s) => s.id === t.id);
                    return (
                      <li key={t.id} className="flex items-center gap-2 px-3 py-2">
                        <span className="min-w-0 flex-1 truncate text-xs text-slate-700">
                          {t.trackNo}. {t.title}
                        </span>
                        <button
                          onClick={() => addTrack(t)}
                          disabled={added}
                          className="rounded border border-brand px-2 py-0.5 text-[11px] font-semibold text-brand disabled:border-slate-200 disabled:text-slate-300"
                        >
                          {added ? "추가됨" : "추가"}
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          </div>
        </div>

        <div className="flex items-center justify-between border-t border-slate-200 px-5 py-3">
          <span className="text-xs text-rose-600">{error}</span>
          <div className="flex gap-2">
            <button onClick={onClose} className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
              취소
            </button>
            <button
              onClick={() => void handleSave()}
              disabled={saving}
              className="inline-flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-60"
            >
              {saving && <Loader2 className="h-4 w-4 animate-spin" />}
              저장
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function PlaylistsPage() {
  const { data: session } = useSession();
  const writable = canWrite(session?.user?.role);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const listQuery = useQuery({
    queryKey: ["playlist-list"],
    queryFn: ({ signal }) => playlistList(signal),
  });
  const playlists: PlaylistDto[] = listQuery.data?.resultObject ?? [];

  const openCreate = () => {
    setEditId(null);
    setMessage(null);
    setDialogOpen(true);
  };
  const openEdit = (id: number) => {
    setEditId(id);
    setMessage(null);
    setDialogOpen(true);
  };
  const handleSaved = (msg: string) => {
    setDialogOpen(false);
    setMessage(msg);
    listQuery.refetch();
  };
  const handleDelete = async (playlist: PlaylistDto) => {
    if (!window.confirm(`'${playlist.title}'을(를) 삭제하시겠습니까?`)) {
      return;
    }
    setMessage(null);
    try {
      const res = await playlistDelete(playlist.id);
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
    <div className="space-y-5">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900">플레이리스트 관리</h1>
          <p className="mt-1 text-sm text-slate-500">음악 탭에 노출되는 큐레이션 플레이리스트를 관리합니다.</p>
        </div>
        {writable && (
          <button
            onClick={openCreate}
            className="inline-flex items-center gap-1.5 rounded-md bg-brand px-3 py-2 text-sm font-semibold text-white hover:opacity-90"
          >
            <Plus className="h-4 w-4" /> 플레이리스트 등록
          </button>
        )}
      </header>

      {message && <div className="rounded-md bg-brand/10 px-3 py-2 text-sm text-brand">{message}</div>}

      {listQuery.isLoading ? (
        <div className="flex justify-center py-20 text-slate-400">
          <Loader2 className="h-6 w-6 animate-spin" />
        </div>
      ) : playlists.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-300 py-20 text-center text-sm text-slate-400">
          플레이리스트가 없습니다.
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs text-slate-500">
              <tr>
                <th className="px-4 py-2.5">제목</th>
                <th className="px-4 py-2.5">설명</th>
                <th className="px-4 py-2.5">수록곡</th>
                <th className="px-4 py-2.5">정렬</th>
                <th className="px-4 py-2.5">노출</th>
                <th className="px-4 py-2.5 text-right">관리</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {playlists.map((p) => (
                <tr key={p.id}>
                  <td className="px-4 py-2.5 font-medium text-slate-800">{p.title}</td>
                  <td className="px-4 py-2.5 text-slate-500">{p.description}</td>
                  <td className="px-4 py-2.5 text-slate-600">{p.trackCount}곡</td>
                  <td className="px-4 py-2.5 text-slate-600">{p.sortOrder}</td>
                  <td className="px-4 py-2.5">
                    <span
                      className={`rounded px-1.5 py-0.5 text-xs font-semibold ${
                        p.useYn === "Y" ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"
                      }`}
                    >
                      {p.useYn === "Y" ? "노출" : "미노출"}
                    </span>
                  </td>
                  <td className="px-4 py-2.5">
                    <div className="flex justify-end gap-1">
                      <button onClick={() => openEdit(p.id)} className="rounded p-1.5 text-slate-500 hover:bg-slate-100">
                        <Pencil className="h-4 w-4" />
                      </button>
                      {writable && (
                        <button
                          onClick={() => void handleDelete(p)}
                          className="rounded p-1.5 text-rose-500 hover:bg-rose-50"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {dialogOpen && (
        <PlaylistDialog editId={editId} onClose={() => setDialogOpen(false)} onSaved={handleSaved} />
      )}
    </div>
  );
}
