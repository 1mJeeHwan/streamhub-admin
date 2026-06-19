import type { Config } from "tailwindcss";

// ng-front-style px scale: generates { "0px": "0px", ... "Npx": "Npx" }.
// Added via theme.extend so Tailwind defaults (text-sm, w-full, p-4, ...) keep working.
const px = (range: number): Record<string, string> =>
  Array.from({ length: range + 1 }).reduce<Record<string, string>>((acc, _, i) => {
    acc[`${i}px`] = `${i}px`;
    return acc;
  }, {});

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Noto Sans KR"', "Inter", "system-ui", "sans-serif"],
      },
      // ng-front design-system spacing/sizing tokens (additive, 0–200px / 0–100px).
      width: px(200),
      minWidth: px(200),
      maxWidth: px(200),
      height: px(200),
      minHeight: px(200),
      maxHeight: px(200),
      borderWidth: px(100),
      fontSize: px(100),
      lineHeight: { ...px(50), "150%": "150%" },
      padding: px(100),
      margin: px(100),
      letterSpacing: { "0.3": "0.3px" },
      colors: {
        // production-app dark palette (dark-only).
        bg: "rgb(var(--bg) / <alpha-value>)",
        surface: "rgb(var(--surface) / <alpha-value>)",
        card: "rgb(var(--card) / <alpha-value>)",
        border: "rgb(var(--border) / <alpha-value>)",
        active: "rgb(var(--active) / <alpha-value>)",
        inactive: "rgb(var(--inactive) / <alpha-value>)",
        noimg: "rgb(var(--noimg) / <alpha-value>)",
        primary: "rgb(var(--primary) / <alpha-value>)",
        secondary: "rgb(var(--secondary) / <alpha-value>)",
        point: "rgb(var(--point) / <alpha-value>)",
      },
      borderRadius: {
        card: "6px",
      },
      keyframes: {
        "fade-up": {
          "0%": { opacity: "0", transform: "translateY(10px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        shimmer: { "100%": { transform: "translateX(100%)" } },
      },
      animation: {
        "fade-up": "fade-up 0.4s cubic-bezier(0.16, 1, 0.3, 1) both",
        shimmer: "shimmer 1.5s infinite",
      },
    },
  },
  plugins: [],
};

export default config;
