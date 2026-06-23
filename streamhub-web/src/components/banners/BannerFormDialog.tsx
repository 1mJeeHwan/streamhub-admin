"use client";

import { useEffect, useRef, useState } from "react";
import { Images, Loader2, Upload, X } from "lucide-react";

import { mediaList, mediaUpload, type MediaAssetDto } from "@/apis/media";
import {
  useBannerCreate,
  useBannerUpdate,
} from "@/apis/query/banner/banner";
import { contentList } from "@/apis/query/content/content";
import {
  BannerDtoDevice,
  BannerDtoPosition,
  BannerDtoTargetType,
  ContentSearchRequestType,
  type BannerDto,
} from "@/apis/query/streamHubAdminAPI.schemas";
import { SUCCESS_CODE } from "@/types/api";

/**
 * Banner link target type. Content types (VIDEO/MUSIC/POST) store the referenced id; the backend
 * resolves the public path. URL = a raw external/internal url. "" = no link.
 */
type LinkType = "" | "VIDEO" | "MUSIC" | "POST" | "URL";

const LINK_TYPE_LABELS: Record<LinkType, string> = {
  "": "없음",
  VIDEO: "영상",
  MUSIC: "음악",
  POST: "게시글",
  URL: "직접 URL",
};

/** BannerDto plus the structured-link fields (not yet in the generated type until `npm run gen`). */
type BannerWithLink = BannerDto & {
  linkType?: LinkType;
  linkRefId?: number;
  linkLabel?: string;
};

interface LinkSearchResult {
  id: number;
  title: string;
}

const FIELD_CLASS =
  "w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

/**
 * isSafeBannerLinkUrl accepts only an absolute http(s):// URL or a single-leading-slash internal
 * path (e.g. "/music/3"). It rejects scheme-relative "//host" (open-redirect) and dangerous
 * schemes (javascript:, data:, vbscript:, etc.) that would otherwise be stored and later rendered
 * straight into an href on the public site.
 */
function isSafeBannerLinkUrl(raw: string): boolean {
  const value = raw.trim();
  if (!value) {
    return false;
  }
  // Internal path: exactly one leading slash (reject "//host" scheme-relative redirects).
  if (value.startsWith("/")) {
    return !value.startsWith("//");
  }
  // Absolute URL: must parse and use the http/https scheme only.
  try {
    const parsed = new URL(value);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

const POSITION_LABELS: Record<BannerDtoPosition, string> = {
  MAIN_TOP: "메인 상단",
  MAIN_MIDDLE: "메인 중단",
  MAIN_BOTTOM: "메인 하단",
  SIDE: "사이드",
  POPUP: "팝업",
};

const DEVICE_LABELS: Record<BannerDtoDevice, string> = {
  PC: "PC",
  MOBILE: "모바일",
  ALL: "전체",
};

/** Content-tab target on the user site. "" = not a tab banner (legacy main/side banner). */
const TARGET_LABELS: Record<BannerDtoTargetType, string> = {
  VIDEO: "영상 탭",
  SOUND: "음악 탭",
  ALL: "전체 탭",
};

interface BannerFormDialogProps {
  /** Banner being edited, or null/undefined when creating a new one. */
  banner?: BannerDto | null;
  onClose: () => void;
  /** Called after a successful create/update so the parent can refetch. */
  onSaved: () => void;
}

interface BannerFormState {
  title: string;
  subtitle: string;
  position: BannerDtoPosition;
  device: BannerDtoDevice;
  /** "" = not a content-tab banner. */
  targetType: "" | BannerDtoTargetType;
  imageUrl: string;
  /** Structured link target. */
  linkType: LinkType;
  /** Referenced content/post id for content link types. */
  linkRefId: number | null;
  /** Selected content title (display). */
  linkLabel: string;
  /** Raw URL — used only when linkType === "URL". */
  linkUrl: string;
  startAt: string;
  endAt: string;
  sortOrder: string;
  useYn: string;
}

const buildFormState = (banner?: BannerDto | null): BannerFormState => {
  const b = banner as BannerWithLink | null | undefined;
  // Edit: prefer the stored linkType; legacy banners (no type but a url) edit as a raw URL.
  const linkType: LinkType = b?.linkType ?? (b?.linkUrl ? "URL" : "");
  return {
    title: b?.title ?? "",
    subtitle: b?.subtitle ?? "",
    position: b?.position ?? BannerDtoPosition.MAIN_TOP,
    device: b?.device ?? BannerDtoDevice.ALL,
    targetType: b?.targetType ?? "",
    imageUrl: b?.imageUrl ?? "",
    linkType,
    linkRefId: b?.linkRefId ?? null,
    linkLabel: b?.linkLabel ?? "",
    // For content types the response linkUrl is the resolved path — don't show it in the URL box.
    linkUrl: linkType === "URL" ? (b?.linkUrl ?? "") : "",
    startAt: b?.startAt ?? "",
    endAt: b?.endAt ?? "",
    sortOrder: b?.sortOrder != null ? String(b.sortOrder) : "0",
    useYn: b?.useYn ?? "Y",
  };
};

/**
 * BannerFormDialog is a modal create/edit form for a banner. When `banner` has
 * an id it issues an update; otherwise it creates a new banner.
 */
export default function BannerFormDialog({
  banner,
  onClose,
  onSaved,
}: BannerFormDialogProps) {
  const isEdit = banner?.id != null;
  const [form, setForm] = useState<BannerFormState>(() => buildFormState(banner));
  const [message, setMessage] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const createMutation = useBannerCreate();
  const updateMutation = useBannerUpdate();
  const isPending = createMutation.isPending || updateMutation.isPending;

  useEffect(() => {
    setForm(buildFormState(banner));
  }, [banner]);

  const update = <K extends keyof BannerFormState>(
    key: K,
    value: BannerFormState[K],
  ) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  // --- Link target picker (search & select real content/posts) ---
  const [linkKeyword, setLinkKeyword] = useState("");
  const [linkResults, setLinkResults] = useState<LinkSearchResult[]>([]);
  const [linkSearching, setLinkSearching] = useState(false);

  const changeLinkType = (next: LinkType) => {
    // Switching type clears any prior selection/url so we never send a stale ref.
    setForm((prev) => ({ ...prev, linkType: next, linkRefId: null, linkLabel: "", linkUrl: "" }));
    setLinkResults([]);
    setLinkKeyword("");
  };

  const runLinkSearch = async () => {
    const keyword = linkKeyword.trim() || undefined;
    setLinkSearching(true);
    setLinkResults([]);
    try {
      if (form.linkType === "VIDEO" || form.linkType === "MUSIC") {
        const res = await contentList({
          keyword,
          type:
            form.linkType === "MUSIC"
              ? ContentSearchRequestType.SOUND
              : ContentSearchRequestType.VIDEO,
          pageNumber: 0, // backend pagination is 0-based (page 0 = first page)
          pageSize: 8,
        });
        setLinkResults(
          (res.resultObject?.contents ?? []).map((c) => ({
            id: c.id ?? 0,
            title: c.title ?? `#${c.id}`,
          })),
        );
      } else if (form.linkType === "POST") {
        // Home posts have no admin endpoint — search the public feed (no auth needed).
        const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
        const qs = new URLSearchParams({ pageNumber: "0", pageSize: "8" });
        if (keyword) qs.set("keyword", keyword);
        const r = await fetch(`${base}/pub/v1/posts?${qs.toString()}`);
        const j = await r.json();
        const items: Array<{ id?: number; title?: string }> =
          j?.resultObject?.contents ?? j?.resultObject ?? [];
        setLinkResults(items.map((p) => ({ id: p.id ?? 0, title: p.title ?? `#${p.id}` })));
      }
    } catch {
      setMessage("콘텐츠 검색에 실패했습니다.");
    } finally {
      setLinkSearching(false);
    }
  };

  const selectLink = (item: LinkSearchResult) => {
    setForm((prev) => ({ ...prev, linkRefId: item.id, linkLabel: item.title }));
    setLinkResults([]);
    setLinkKeyword("");
  };

  // --- Media library picker (browse already-uploaded images) ---
  const [showLibrary, setShowLibrary] = useState(false);
  const [libraryItems, setLibraryItems] = useState<MediaAssetDto[]>([]);
  const [libraryLoading, setLibraryLoading] = useState(false);

  const toggleLibrary = async () => {
    const next = !showLibrary;
    setShowLibrary(next);
    if (next && libraryItems.length === 0) {
      setLibraryLoading(true);
      try {
        // 0-based pagination (page 0 = first page).
        const res = await mediaList({ pageNumber: 0, pageSize: 24 });
        setLibraryItems(res.resultObject?.contents ?? []);
      } catch {
        setMessage("미디어 라이브러리를 불러오지 못했습니다.");
      } finally {
        setLibraryLoading(false);
      }
    }
  };

  const handleImageUpload = async (file: File | undefined) => {
    if (!file) {
      return;
    }
    if (!file.type.startsWith("image/")) {
      setMessage("이미지 파일만 업로드할 수 있습니다.");
      return;
    }
    setMessage(null);
    setUploading(true);
    try {
      const asset = await mediaUpload(file, "banner");
      if (asset?.url) {
        update("imageUrl", asset.url);
      } else {
        setMessage("업로드 응답이 올바르지 않습니다.");
      }
    } catch {
      setMessage("이미지 업로드에 실패했습니다.");
    } finally {
      setUploading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  };

  const handleResult = (resultCode?: string, resultMessage?: string) => {
    if (resultCode === SUCCESS_CODE) {
      onSaved();
    } else {
      setMessage(resultMessage ?? "저장에 실패했습니다.");
    }
  };

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    setMessage(null);

    if (!form.title.trim()) {
      setMessage("제목을 입력해 주세요.");
      return;
    }

    const isContentLink =
      form.linkType === "VIDEO" || form.linkType === "MUSIC" || form.linkType === "POST";
    if (isContentLink && form.linkRefId == null) {
      setMessage(`링크할 ${LINK_TYPE_LABELS[form.linkType]} 콘텐츠를 검색해서 선택해 주세요.`);
      return;
    }

    // URL link: only allow http(s):// or a single-leading-slash internal path. Blocks
    // javascript:/data:/vbscript: and scheme-relative "//host" before the value is stored.
    if (form.linkType === "URL") {
      const trimmedUrl = form.linkUrl.trim();
      if (trimmedUrl && !isSafeBannerLinkUrl(trimmedUrl)) {
        setMessage(
          "링크 URL은 http:// 또는 https:// 로 시작하거나 /로 시작하는 내부 경로여야 합니다.",
        );
        return;
      }
    }

    const parsedSort = Number(form.sortOrder);
    const payload: BannerWithLink = {
      title: form.title.trim(),
      subtitle: form.subtitle.trim() || undefined,
      position: form.position,
      device: form.device,
      targetType: form.targetType || undefined,
      imageUrl: form.imageUrl.trim() || undefined,
      // Structured link: content types send type+refId+label (linkUrl resolved server-side);
      // URL type sends the raw url; "" clears everything.
      linkType: form.linkType || undefined,
      linkRefId: isContentLink ? form.linkRefId ?? undefined : undefined,
      linkLabel: isContentLink ? form.linkLabel || undefined : undefined,
      linkUrl: form.linkType === "URL" ? form.linkUrl.trim() || undefined : undefined,
      startAt: form.startAt.trim() || undefined,
      endAt: form.endAt.trim() || undefined,
      sortOrder: Number.isFinite(parsedSort) ? parsedSort : 0,
      useYn: form.useYn,
    };

    if (isEdit && banner?.id != null) {
      updateMutation.mutate(
        { id: banner.id, data: payload },
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
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-md bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
          <h2 className="text-base font-semibold text-slate-900">
            {isEdit ? "배너 수정" : "배너 등록"}
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

        <form onSubmit={handleSubmit} className="px-5 py-4" noValidate>
          {message && (
            <p className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
              {message}
            </p>
          )}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {/* Title */}
            <div className="sm:col-span-2">
              <label
                htmlFor="banner-title"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                제목 *
              </label>
              <input
                id="banner-title"
                type="text"
                className={FIELD_CLASS}
                value={form.title}
                onChange={(event) => update("title", event.target.value)}
              />
            </div>

            {/* Subtitle */}
            <div className="sm:col-span-2">
              <label
                htmlFor="banner-subtitle"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                부제 (탭 배너 설명)
              </label>
              <input
                id="banner-subtitle"
                type="text"
                className={FIELD_CLASS}
                value={form.subtitle}
                onChange={(event) => update("subtitle", event.target.value)}
              />
            </div>

            {/* Target tab */}
            <div className="sm:col-span-2">
              <label
                htmlFor="banner-target"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                사용자 사이트 탭 노출
              </label>
              <select
                id="banner-target"
                className={FIELD_CLASS}
                value={form.targetType}
                onChange={(event) =>
                  update("targetType", event.target.value as "" | BannerDtoTargetType)
                }
              >
                <option value="">노출 안 함 (일반 배너)</option>
                {Object.values(BannerDtoTargetType).map((target) => (
                  <option key={target} value={target}>
                    {TARGET_LABELS[target]}
                  </option>
                ))}
              </select>
            </div>

            {/* Position */}
            <div>
              <label
                htmlFor="banner-position"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                노출 위치
              </label>
              <select
                id="banner-position"
                className={FIELD_CLASS}
                value={form.position}
                onChange={(event) =>
                  update("position", event.target.value as BannerDtoPosition)
                }
              >
                {Object.values(BannerDtoPosition).map((position) => (
                  <option key={position} value={position}>
                    {POSITION_LABELS[position]}
                  </option>
                ))}
              </select>
            </div>

            {/* Device */}
            <div>
              <label
                htmlFor="banner-device"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                디바이스
              </label>
              <select
                id="banner-device"
                className={FIELD_CLASS}
                value={form.device}
                onChange={(event) =>
                  update("device", event.target.value as BannerDtoDevice)
                }
              >
                {Object.values(BannerDtoDevice).map((device) => (
                  <option key={device} value={device}>
                    {DEVICE_LABELS[device]}
                  </option>
                ))}
              </select>
            </div>

            {/* Image URL */}
            <div className="sm:col-span-2">
              <label
                htmlFor="banner-image"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                이미지 (탭 배너는 비워두면 그라데이션) — 업로드 시 미디어 라이브러리에 저장됩니다
              </label>
              <div className="flex items-center gap-2">
                <input
                  id="banner-image"
                  type="text"
                  placeholder="https://... 또는 이미지 업로드"
                  className={FIELD_CLASS}
                  value={form.imageUrl}
                  onChange={(event) => update("imageUrl", event.target.value)}
                />
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={uploading}
                  className="inline-flex shrink-0 items-center gap-1.5 rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
                >
                  {uploading ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Upload className="h-4 w-4" />
                  )}
                  업로드
                </button>
                <button
                  type="button"
                  onClick={() => void toggleLibrary()}
                  className="inline-flex shrink-0 items-center gap-1.5 rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  <Images className="h-4 w-4" />
                  라이브러리
                </button>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/*"
                  hidden
                  onChange={(event) => void handleImageUpload(event.target.files?.[0])}
                />
              </div>

              {showLibrary && (
                <div className="mt-2 rounded-md border border-slate-200 p-2">
                  {libraryLoading ? (
                    <div className="flex h-24 items-center justify-center">
                      <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
                    </div>
                  ) : libraryItems.length === 0 ? (
                    <p className="py-6 text-center text-sm text-slate-400">
                      업로드된 이미지가 없습니다. 먼저 업로드해 주세요.
                    </p>
                  ) : (
                    <div className="grid max-h-56 grid-cols-3 gap-2 overflow-y-auto sm:grid-cols-4">
                      {libraryItems.map((asset) => (
                        <button
                          key={asset.id}
                          type="button"
                          onClick={() => {
                            update("imageUrl", asset.url);
                            setShowLibrary(false);
                          }}
                          title={asset.originalName ?? asset.key}
                          className={`overflow-hidden rounded-md border-2 transition ${
                            form.imageUrl === asset.url
                              ? "border-brand"
                              : "border-transparent hover:border-slate-300"
                          }`}
                        >
                          {/* eslint-disable-next-line @next/next/no-img-element */}
                          <img
                            src={asset.url}
                            alt={asset.originalName ?? ""}
                            className="h-16 w-full object-cover"
                          />
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {form.imageUrl && (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={form.imageUrl}
                  alt=""
                  className="mt-2 h-24 w-full max-w-xs rounded-md border border-slate-200 object-cover"
                />
              )}
            </div>

            {/* Link target — pick real content/post, or a raw URL */}
            <div className="sm:col-span-2">
              <label
                htmlFor="banner-linktype"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                링크 대상
              </label>
              <select
                id="banner-linktype"
                className={FIELD_CLASS}
                value={form.linkType}
                onChange={(event) => changeLinkType(event.target.value as LinkType)}
              >
                {(Object.keys(LINK_TYPE_LABELS) as LinkType[]).map((t) => (
                  <option key={t || "none"} value={t}>
                    {LINK_TYPE_LABELS[t]}
                  </option>
                ))}
              </select>

              {form.linkType === "URL" && (
                <input
                  type="text"
                  placeholder="https://... 또는 /내부경로"
                  className={`${FIELD_CLASS} mt-2`}
                  value={form.linkUrl}
                  onChange={(event) => update("linkUrl", event.target.value)}
                />
              )}

              {(form.linkType === "VIDEO" ||
                form.linkType === "MUSIC" ||
                form.linkType === "POST") && (
                <div className="mt-2 space-y-2">
                  {form.linkRefId != null ? (
                    <div className="flex items-center justify-between rounded-md border border-brand/40 bg-brand/5 px-3 py-2 text-sm">
                      <span className="truncate text-slate-800">
                        선택됨: {form.linkLabel || `#${form.linkRefId}`}{" "}
                        <span className="text-slate-400">(#{form.linkRefId})</span>
                      </span>
                      <button
                        type="button"
                        onClick={() =>
                          setForm((p) => ({ ...p, linkRefId: null, linkLabel: "" }))
                        }
                        className="ml-2 shrink-0 text-xs text-slate-500 hover:text-red-600"
                      >
                        제거
                      </button>
                    </div>
                  ) : (
                    <>
                      <div className="flex gap-2">
                        <input
                          type="text"
                          placeholder={`${LINK_TYPE_LABELS[form.linkType]} 제목 검색`}
                          className={FIELD_CLASS}
                          value={linkKeyword}
                          onChange={(event) => setLinkKeyword(event.target.value)}
                          onKeyDown={(event) => {
                            if (event.key === "Enter") {
                              event.preventDefault();
                              void runLinkSearch();
                            }
                          }}
                        />
                        <button
                          type="button"
                          onClick={() => void runLinkSearch()}
                          disabled={linkSearching}
                          className="inline-flex shrink-0 items-center rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
                        >
                          {linkSearching ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            "검색"
                          )}
                        </button>
                      </div>
                      {linkResults.length > 0 && (
                        <ul className="max-h-44 overflow-y-auto rounded-md border border-slate-200">
                          {linkResults.map((r) => (
                            <li key={r.id}>
                              <button
                                type="button"
                                onClick={() => selectLink(r)}
                                className="block w-full truncate px-3 py-2 text-left text-sm hover:bg-slate-50"
                              >
                                {r.title}{" "}
                                <span className="text-slate-400">(#{r.id})</span>
                              </button>
                            </li>
                          ))}
                        </ul>
                      )}
                    </>
                  )}
                  <p className="text-[11px] text-slate-400">
                    선택한 콘텐츠로 자동 이동합니다 (클릭 시{" "}
                    {form.linkType === "POST"
                      ? "/posts"
                      : form.linkType === "MUSIC"
                        ? "/music"
                        : "/video"}
                    /&lt;id&gt;).
                  </p>
                </div>
              )}
            </div>

            {/* Start at */}
            <div>
              <label
                htmlFor="banner-start"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                노출 시작
              </label>
              <input
                id="banner-start"
                type="datetime-local"
                className={FIELD_CLASS}
                value={form.startAt}
                onChange={(event) => update("startAt", event.target.value)}
              />
            </div>

            {/* End at */}
            <div>
              <label
                htmlFor="banner-end"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                노출 종료
              </label>
              <input
                id="banner-end"
                type="datetime-local"
                className={FIELD_CLASS}
                value={form.endAt}
                onChange={(event) => update("endAt", event.target.value)}
              />
            </div>

            {/* Sort order */}
            <div>
              <label
                htmlFor="banner-sort"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                정렬 순서
              </label>
              <input
                id="banner-sort"
                type="number"
                className={FIELD_CLASS}
                value={form.sortOrder}
                onChange={(event) => update("sortOrder", event.target.value)}
              />
            </div>

            {/* Use yn */}
            <div>
              <label
                htmlFor="banner-use"
                className="mb-1 block text-xs font-medium text-slate-500"
              >
                사용 여부
              </label>
              <select
                id="banner-use"
                className={FIELD_CLASS}
                value={form.useYn}
                onChange={(event) => update("useYn", event.target.value)}
              >
                <option value="Y">사용</option>
                <option value="N">미사용</option>
              </select>
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
