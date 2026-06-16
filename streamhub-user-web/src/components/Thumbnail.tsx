"use client";

import { useState } from "react";
import { Film, Music } from "lucide-react";
import clsx from "clsx";
import type { ContentType } from "@/lib/types";

/**
 * Thumbnail with graceful fallback. Seed content has no thumbnail and remote images can
 * fail, so both paths fall back to a placeholder (production-app no_image tone) — never a broken image.
 */
export function Thumbnail({
  url,
  type,
  title,
  ratio = "video",
}: {
  url: string | null;
  type: ContentType;
  title: string;
  ratio?: "video" | "square";
}) {
  const [failed, setFailed] = useState(false);
  const Icon = type === "SOUND" ? Music : Film;
  const showImage = url && !failed;

  return (
    <div className={clsx("thumb", ratio === "square" ? "aspect-square" : "aspect-video")}>
      {showImage ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={url}
          alt={title}
          loading="lazy"
          onError={() => setFailed(true)}
          className="h-full w-full object-cover"
        />
      ) : (
        <div className="absolute inset-0 grid place-items-center bg-gradient-to-br from-card to-surface">
          <Icon className="h-8 w-8 text-inactive" />
        </div>
      )}
    </div>
  );
}
