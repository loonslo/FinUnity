import { createContext, useEffect, useState } from "react";

/** 极简灰蓝 — light chrome throughout; 藏青 — dark sidebar + dark hero card. */
export type ThemeColor = "minimal" | "navy";
/** `system` follows the OS via prefers-color-scheme. */
export type Appearance = "system" | "light" | "dark";

export type ThemeApi = {
  themeColor: ThemeColor;
  appearance: Appearance;
  setThemeColor: (value: ThemeColor) => void;
  setAppearance: (value: Appearance) => void;
};

/**
 * Shared so the single `useTheme()` owner (Root) can publish the state and any
 * deeply nested dialog can read it without prop drilling. `null` until the
 * provider mounts, which never happens in practice — Root wraps everything.
 */
export const ThemeContext = createContext<ThemeApi | null>(null);

const COLOR_KEY = "finunity-theme-color";
const APPEARANCE_KEY = "finunity-appearance";

const isThemeColor = (v: unknown): v is ThemeColor =>
  v === "minimal" || v === "navy";
const isAppearance = (v: unknown): v is Appearance =>
  v === "system" || v === "light" || v === "dark";

function readStored<T>(
  key: string,
  guard: (v: unknown) => v is T,
  fallback: T,
): T {
  try {
    const raw = localStorage.getItem(key);
    return guard(raw) ? raw : fallback;
  } catch {
    return fallback; // storage disabled (private mode, embedded webview)
  }
}

function writeStored(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* preference simply won't persist */
  }
}

/**
 * Drives `data-theme-color` and `data-appearance` on <html>, which is all the
 * token file needs to re-skin the app. Omitting `data-appearance` lets the OS
 * decide, matching the `:not([data-appearance="light"])` branches in the CSS.
 */
export function useTheme() {
  const [themeColor, setThemeColorState] = useState<ThemeColor>(() =>
    readStored(COLOR_KEY, isThemeColor, "minimal"),
  );
  const [appearance, setAppearanceState] = useState<Appearance>(() =>
    readStored(APPEARANCE_KEY, isAppearance, "system"),
  );

  useEffect(() => {
    const root = document.documentElement;
    root.dataset.themeColor = themeColor;
    if (appearance === "system") delete root.dataset.appearance;
    else root.dataset.appearance = appearance;
  }, [themeColor, appearance]);

  const setThemeColor = (value: ThemeColor) => {
    writeStored(COLOR_KEY, value);
    setThemeColorState(value);
  };
  const setAppearance = (value: Appearance) => {
    writeStored(APPEARANCE_KEY, value);
    setAppearanceState(value);
  };

  return { themeColor, appearance, setThemeColor, setAppearance };
}
