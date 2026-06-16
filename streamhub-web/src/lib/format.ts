/**
 * formatDate converts an ISO date/datetime string to `YYYY-MM-DD`.
 * Returns "-" when the input is missing or unparseable.
 */
export function formatDate(value?: string | null): string {
  if (!value) {
    return "-";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    // Fall back to a date-only prefix when the value is already date-like.
    return value.slice(0, 10);
  }

  const year = parsed.getFullYear();
  const month = String(parsed.getMonth() + 1).padStart(2, "0");
  const day = String(parsed.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * formatDateTime converts an ISO datetime to `YYYY-MM-DD HH:mm`.
 * Returns "-" when the input is missing or unparseable.
 */
export function formatDateTime(value?: string | null): string {
  if (!value) {
    return "-";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value.slice(0, 16).replace("T", " ");
  }
  const y = parsed.getFullYear();
  const mo = String(parsed.getMonth() + 1).padStart(2, "0");
  const d = String(parsed.getDate()).padStart(2, "0");
  const h = String(parsed.getHours()).padStart(2, "0");
  const mi = String(parsed.getMinutes()).padStart(2, "0");
  return `${y}-${mo}-${d} ${h}:${mi}`;
}

/**
 * formatNumber renders a number with thousands separators (e.g. 12345 → "12,345").
 * Returns "0" when the input is missing or not a finite number.
 */
export function formatNumber(value?: number | null): string {
  if (value == null || Number.isNaN(value)) {
    return "0";
  }

  return value.toLocaleString("ko-KR");
}

/**
 * secondsToHours converts a duration in seconds to hours rounded to one
 * decimal place (e.g. 5400 → 1.5). Returns 0 for missing or invalid input.
 */
export function secondsToHours(seconds?: number | null): number {
  if (seconds == null || seconds < 0 || Number.isNaN(seconds)) {
    return 0;
  }

  return Math.round((seconds / 3600) * 10) / 10;
}

/**
 * formatDuration converts a duration in seconds to a `m:ss` string
 * (e.g. 125 → "2:05"). Hours roll up into the minutes field.
 * Returns "-" when the input is missing or negative.
 */
export function formatDuration(seconds?: number | null): string {
  if (seconds == null || seconds < 0 || Number.isNaN(seconds)) {
    return "-";
  }

  const total = Math.floor(seconds);
  const minutes = Math.floor(total / 60);
  const secs = total % 60;
  return `${minutes}:${String(secs).padStart(2, "0")}`;
}
