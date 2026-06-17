import clsx from "clsx";

/** Honest "demo preview" badge — the preview audio is a SoundHelix sample, not a real label master. */
export function DemoBadge({ label = "데모 미리듣기 · 30초", className }: { label?: string; className?: string }) {
  return (
    <span
      className={clsx(
        "inline-flex items-center gap-1 rounded-full border border-primary/40 bg-primary/10 px-2 py-0.5 text-[10px] font-bold text-primary",
        className,
      )}
    >
      {label}
    </span>
  );
}
