"use client";

import { useState } from "react";
import { Disc3 } from "lucide-react";
import clsx from "clsx";

/**
 * Small square album-cover thumbnail with a graceful fallback, mirroring AlbumCard's <img>
 * + onError pattern. Used by the purchased-album and playlist rows in mypage.
 */
export function CoverThumb({
  src,
  alt,
  size = 48,
}: {
  src: string | null;
  alt: string;
  size?: number;
}) {
  const [failed, setFailed] = useState(false);
  const showImage = src && !failed;

  return (
    <div
      className="relative shrink-0 overflow-hidden rounded-lg bg-card"
      style={{ width: size, height: size }}
    >
      {showImage ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={src ?? ""}
          alt={alt}
          loading="lazy"
          onError={() => setFailed(true)}
          className="h-full w-full object-cover"
        />
      ) : (
        <div className={clsx("grid h-full w-full place-items-center bg-gradient-to-br from-card to-surface")}>
          <Disc3 className="h-5 w-5 text-inactive" />
        </div>
      )}
    </div>
  );
}
