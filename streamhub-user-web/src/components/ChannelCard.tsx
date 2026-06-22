"use client";

import type { PublicChannel } from "@/lib/api";

/**
 * A channel tile in the video channel-browse carousel. A button (not a link) so selecting a
 * channel filters the list in place via local state — the page's keyword search owns the URL
 * (?q=), so a ?channel= param would be clobbered by it.
 */
export function ChannelCard({
  channel,
  onSelect,
  active = false,
}: {
  channel: PublicChannel;
  onSelect: () => void;
  active?: boolean;
}) {
  const initial = channel.name.trim().charAt(0) || "채";

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={active}
      style={{ width: 132 }}
      className="block text-left"
    >
      <div
        className="thumb aspect-square grid place-items-center bg-gradient-to-br from-card to-surface ring-inset transition-all data-[active=true]:ring-2 data-[active=true]:ring-primary"
        data-active={active}
      >
        <span className="grid h-16 w-16 place-items-center rounded-full bg-primary/15 text-28px font-bold text-primary">
          {initial}
        </span>
        <span className="absolute bottom-1.5 right-1.5 rounded bg-bg/70 px-1.5 py-0.5 text-[10px] font-bold text-active backdrop-blur">
          {channel.contentCount}편
        </span>
      </div>
      <p className="ellipsis-1 mt-10px text-14px font-bold leading-20px text-active">{channel.name}</p>
    </button>
  );
}
