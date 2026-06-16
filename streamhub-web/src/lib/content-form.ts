/** Shared helpers for the content create/edit forms (add + [id]). */

/** Common input styling used across both content forms. */
export const FIELD_CLASS =
  "w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand";

/**
 * Parses a comma-separated hashtag string into a clean list.
 * "찬양, #예배 ," → ["찬양", "예배"]; empty/whitespace → undefined.
 */
export function parseHashtags(input?: string): string[] | undefined {
  if (!input) {
    return undefined;
  }
  const list = input
    .split(",")
    .map((tag) => tag.trim().replace(/^#/, ""))
    .filter((tag) => tag.length > 0);
  return list.length > 0 ? list : undefined;
}
