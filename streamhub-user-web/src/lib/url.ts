/**
 * safeHref returns a render-safe href for a value that may have come from admin/user input.
 * It allows only absolute http(s):// URLs and single-leading-slash internal paths; anything else
 * (javascript:/data:/vbscript:, scheme-relative "//host", malformed values, null) falls back to
 * the given `fallback` ("#" by default). Defense-in-depth against stored XSS / open-redirect when a
 * stored linkUrl is rendered straight into an anchor href.
 */
export function safeHref(raw: string | null | undefined, fallback = "#"): string {
  if (!raw) {
    return fallback;
  }
  const value = raw.trim();
  if (!value) {
    return fallback;
  }
  // Internal path: exactly one leading slash (reject "//host" scheme-relative redirects).
  if (value.startsWith("/")) {
    return value.startsWith("//") ? fallback : value;
  }
  // Absolute URL: must parse and use the http/https scheme only.
  try {
    const parsed = new URL(value);
    return parsed.protocol === "http:" || parsed.protocol === "https:" ? value : fallback;
  } catch {
    return fallback;
  }
}
