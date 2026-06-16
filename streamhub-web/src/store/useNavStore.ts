import { create } from "zustand";

interface NavState {
  /** Whether the sidebar is expanded (desktop) or open (mobile). */
  isOpen: boolean;
  /** The currently active menu href, e.g. "/dashboard". */
  activeHref: string;
  toggle: () => void;
  setOpen: (open: boolean) => void;
  setActiveHref: (href: string) => void;
}

/**
 * useNavStore holds sidebar open/active UI state shared across the shell.
 */
export const useNavStore = create<NavState>((set) => ({
  isOpen: true,
  activeHref: "/dashboard",
  toggle: () => set((state) => ({ isOpen: !state.isOpen })),
  setOpen: (open) => set({ isOpen: open }),
  setActiveHref: (href) => set({ activeHref: href }),
}));
