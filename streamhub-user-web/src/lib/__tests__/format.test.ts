import { describe, expect, it } from "vitest";
import { formatDate, formatDuration, formatViews, splitHashtags } from "../format";

describe("formatDuration", () => {
  it("returns empty for null/zero", () => {
    expect(formatDuration(null)).toBe("");
    expect(formatDuration(0)).toBe("");
  });
  it("formats mm:ss under an hour", () => {
    expect(formatDuration(90)).toBe("1:30");
    expect(formatDuration(605)).toBe("10:05");
  });
  it("formats h:mm:ss over an hour", () => {
    expect(formatDuration(3661)).toBe("1:01:01");
  });
});

describe("formatViews", () => {
  it("keeps small counts as-is", () => {
    expect(formatViews(0)).toBe("0");
    expect(formatViews(999)).toBe("999");
  });
  it("compacts thousands (천) and ten-thousands (만)", () => {
    expect(formatViews(1500)).toBe("1.5천");
    expect(formatViews(23000)).toBe("2.3만");
  });
  it("drops trailing .0", () => {
    expect(formatViews(2000)).toBe("2천");
    expect(formatViews(10000)).toBe("1만");
  });
});

describe("formatDate", () => {
  it("formats ISO to y.mm.dd", () => {
    expect(formatDate("2026-06-16T10:00:00")).toBe("2026.06.16");
  });
  it("returns empty for null/invalid", () => {
    expect(formatDate(null)).toBe("");
    expect(formatDate("not-a-date")).toBe("");
  });
});

describe("splitHashtags", () => {
  it("splits on commas, trims, and drops blank entries", () => {
    expect(splitHashtags("예배, 찬양 , ,설교")).toEqual(["예배", "찬양", "설교"]);
  });
  it("returns empty array for null", () => {
    expect(splitHashtags(null)).toEqual([]);
  });
});
