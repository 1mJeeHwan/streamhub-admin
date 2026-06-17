/**
 * relativeTime renders a server-provided ISO datetime as a Korean relative
 * label (e.g. "3분 전", "방금 전") measured against the client's current clock.
 * The demo runs a single organization with a unified timezone, so a plain
 * `Date.now()` difference is sufficient. Returns "-" for missing or unparseable
 * input.
 */
export function relativeTime(value?: string | null): string {
  if (!value) {
    return "-";
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "-";
  }

  const diffMs = Date.now() - parsed.getTime();
  const diffSec = Math.floor(diffMs / 1000);

  if (diffSec < 0) {
    return "방금 전";
  }
  if (diffSec < 60) {
    return "방금 전";
  }

  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) {
    return `${diffMin}분 전`;
  }

  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) {
    return `${diffHour}시간 전`;
  }

  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 7) {
    return `${diffDay}일 전`;
  }

  const diffWeek = Math.floor(diffDay / 7);
  if (diffWeek < 5) {
    return `${diffWeek}주 전`;
  }

  const diffMonth = Math.floor(diffDay / 30);
  if (diffMonth < 12) {
    return `${diffMonth}개월 전`;
  }

  const diffYear = Math.floor(diffDay / 365);
  return `${diffYear}년 전`;
}
