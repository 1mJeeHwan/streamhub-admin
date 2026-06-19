import type { CSSProperties, ReactNode } from "react";

/**
 * Horizontal carousel, ported from ng-front's ItemCarousel compound pattern.
 * Uses the CSS scroll-snap row (.hrow: gap-8px, hidden scrollbar) instead of
 * Embla — same structure, no extra dependency.
 *
 *   <ItemCarousel>
 *     <ItemCarousel.ItemWrapper><ContentCard item={x} size="lg" /></ItemCarousel.ItemWrapper>
 *   </ItemCarousel>
 */
export function ItemCarousel({ children }: { children: ReactNode }) {
  return (
    <div className="relative">
      <div className="hrow px-20px pb-1">{children}</div>
    </div>
  );
}

/** A single carousel slot. Omit `width` to size to the child's intrinsic width. */
function ItemWrapper({ width, children }: { width?: number | string; children: ReactNode }) {
  const basis = width == null ? "auto" : typeof width === "number" ? `${width}px` : width;
  const style: CSSProperties = { flex: `0 0 ${basis}` };
  return (
    <div style={style} className="relative min-w-0">
      {children}
    </div>
  );
}

ItemCarousel.ItemWrapper = ItemWrapper;
