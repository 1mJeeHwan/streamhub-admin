import { createElement, type HTMLAttributes, type ReactNode } from "react";

/** Allowed semantic tags (avoids stray divs). */
type TagVariant = "h1" | "h2" | "h3" | "h4" | "h5" | "h6" | "p" | "span";

/** ng-front type scale, ported 1:1 (font-size / line-height / weight / tracking). */
const styles = {
  h1: "text-22px leading-30px font-bold tracking-normal",
  h2: "text-20px leading-20px font-medium tracking-normal",
  h3: "text-16px leading-20px font-bold tracking-normal",
  h4: "text-14px leading-18px font-normal tracking-normal",
  sub1: "text-11px leading-14px font-normal tracking-normal",
  body1: "text-15px leading-150% -tracking-0.3",
  body2: "text-14px leading-150% -tracking-0.3",
  button: "text-15px leading-18px -tracking-0.3",
} as const;

export type TypographyType = keyof typeof styles;

type TypographyProps = {
  tag?: TagVariant;
  type?: TypographyType;
  children: ReactNode;
} & HTMLAttributes<HTMLElement>;

/** Renders text with a preset from the ng-front type scale. */
export function Typography({ tag = "p", type, children, className, ...props }: TypographyProps) {
  const preset = type ? styles[type] : "";
  return createElement(
    tag,
    { ...props, className: [preset, className].filter(Boolean).join(" ") },
    children,
  );
}

export default Typography;
