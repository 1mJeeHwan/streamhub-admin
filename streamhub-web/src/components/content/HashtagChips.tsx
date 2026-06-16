interface HashtagChipsProps {
  /** Hashtags as an array (detail) or a comma-separated string (list). */
  tags?: string[] | string | null;
}

function toTagList(tags?: string[] | string | null): string[] {
  if (!tags) {
    return [];
  }
  const raw = Array.isArray(tags) ? tags : tags.split(",");
  return raw.map((tag) => tag.trim()).filter((tag) => tag.length > 0);
}

/**
 * HashtagChips renders hashtags as small pills. Accepts either an array
 * (ContentDetail) or a comma-separated string (ContentListItem). Renders a
 * dash when there are no tags.
 */
export default function HashtagChips({ tags }: HashtagChipsProps) {
  const list = toTagList(tags);

  if (list.length === 0) {
    return <span className="text-sm text-slate-400">-</span>;
  }

  return (
    <div className="flex flex-wrap gap-1">
      {list.map((tag) => (
        <span
          key={tag}
          className="inline-flex items-center rounded-md bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600"
        >
          #{tag}
        </span>
      ))}
    </div>
  );
}
