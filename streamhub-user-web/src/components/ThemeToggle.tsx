"use client";

import { useEffect, useState } from "react";
import { Moon, Sun } from "lucide-react";

/** localStorage key shared with the no-flash inline script in the root layout. */
const THEME_KEY = "streamhub.theme";
type Theme = "dark" | "light";

/** Reads the theme currently applied to <html> (set by the no-flash script), defaulting to light. */
function currentTheme(): Theme {
  if (typeof document === "undefined") return "light";
  return document.documentElement.getAttribute("data-theme") === "dark" ? "dark" : "light";
}

/**
 * Light/dark theme switch. Toggling sets `data-theme` on <html> (which flips the CSS
 * token palette in globals.css) and persists the choice to localStorage. A no-flash
 * inline script in the root layout applies the stored theme before first paint.
 * Renders nothing until mounted to avoid a hydration mismatch on the icon.
 */
export function ThemeToggle() {
  const [mounted, setMounted] = useState(false);
  const [theme, setTheme] = useState<Theme>("light");

  useEffect(() => {
    setTheme(currentTheme());
    setMounted(true);
  }, []);

  const toggle = () => {
    const next: Theme = theme === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", next);
    try {
      window.localStorage.setItem(THEME_KEY, next);
    } catch {
      /* storage unavailable (private mode) — theme still applies for this session */
    }
    setTheme(next);
  };

  if (!mounted) {
    // Reserve the slot so the AppBar layout doesn't shift after hydration.
    return <span className="inline-block h-5 w-5" aria-hidden />;
  }

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={theme === "dark" ? "라이트 모드로 전환" : "다크 모드로 전환"}
      className="text-active transition-colors hover:text-primary"
    >
      {theme === "dark" ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
    </button>
  );
}
