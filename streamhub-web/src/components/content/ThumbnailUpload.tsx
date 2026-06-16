"use client";

import { useRef, useState, type DragEvent } from "react";
import { ImagePlus, Loader2, X } from "lucide-react";

import { uploadFile } from "@/apis/upload";

interface ThumbnailUploadProps {
  /** Currently selected/saved thumbnail url for preview, if any. */
  previewUrl?: string;
  /** Called with the uploaded storage key + url after a successful upload. */
  onUploaded: (result: { key: string; url: string }) => void;
  /** Called when the user clears the current thumbnail. */
  onClear?: () => void;
}

/**
 * ThumbnailUpload is a drag-and-drop (or click-to-select) image upload zone.
 * On drop/select it uploads the image via `uploadFile`, shows a spinner while
 * in flight, then previews the returned url and reports the storage key back
 * to the parent form through `onUploaded`.
 */
export default function ThumbnailUpload({
  previewUrl,
  onUploaded,
  onClear,
}: ThumbnailUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleFile = async (file: File) => {
    if (!file.type.startsWith("image/")) {
      setError("이미지 파일만 업로드할 수 있습니다.");
      return;
    }

    setError(null);
    setIsUploading(true);
    try {
      const result = await uploadFile(file);
      if (result.key && result.url) {
        onUploaded({ key: result.key, url: result.url });
      } else {
        setError("업로드 응답이 올바르지 않습니다.");
      }
    } catch {
      setError("업로드에 실패했습니다.");
    } finally {
      setIsUploading(false);
    }
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragging(false);
    const file = event.dataTransfer.files?.[0];
    if (file) {
      void handleFile(file);
    }
  };

  const handleDragOver = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragging(false);
  };

  const openPicker = () => {
    if (!isUploading) {
      inputRef.current?.click();
    }
  };

  return (
    <div>
      <div
        role="button"
        tabIndex={0}
        onClick={openPicker}
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            openPicker();
          }
        }}
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        className={`relative flex h-40 w-full cursor-pointer flex-col items-center justify-center gap-2 rounded-md border-2 border-dashed text-sm transition ${
          isDragging
            ? "border-brand bg-brand/5"
            : "border-slate-300 bg-slate-50 hover:border-brand"
        }`}
      >
        {isUploading ? (
          <div className="flex flex-col items-center gap-2 text-slate-500">
            <Loader2 className="h-6 w-6 animate-spin" />
            <span>업로드 중...</span>
          </div>
        ) : previewUrl ? (
          <>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={previewUrl}
              alt="썸네일 미리보기"
              className="h-full w-full rounded-md object-contain"
            />
            {onClear && (
              <button
                type="button"
                onClick={(event) => {
                  event.stopPropagation();
                  onClear();
                }}
                className="absolute right-2 top-2 rounded-full bg-white/90 p-1 text-slate-600 shadow hover:text-red-600"
                aria-label="썸네일 제거"
              >
                <X className="h-4 w-4" />
              </button>
            )}
          </>
        ) : (
          <div className="flex flex-col items-center gap-2 text-slate-500">
            <ImagePlus className="h-6 w-6" />
            <span>이미지를 끌어다 놓거나 클릭하여 업로드</span>
          </div>
        )}
      </div>

      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) {
            void handleFile(file);
          }
          // Reset so selecting the same file again still fires onChange.
          event.target.value = "";
        }}
      />

      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  );
}
