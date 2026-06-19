"use client";

import clsx from "clsx";

/**
 * Thin "preparing to play" progress bar shown while an HLS stream loads, until it can play.
 * The parent unmounts it once playback is ready.
 */
export function HlsLoadingBar({
  progress,
  label = "재생 준비 중",
  className,
}: {
  progress: number;
  label?: string;
  className?: string;
}) {
  const pct = Math.max(5, Math.min(100, Math.round(progress)));
  return (
    <div className={clsx("mt-2", className)} role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
      <div className="h-1 w-full overflow-hidden rounded-full bg-border">
        <div
          className="h-full rounded-full bg-primary transition-[width] duration-300 ease-out"
          style={{ width: `${pct}%` }}
        />
      </div>
      <p className="mt-1 text-center text-[11px] text-inactive">
        {label} {pct}%
      </p>
    </div>
  );
}
