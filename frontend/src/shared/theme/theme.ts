export type ThemePreference = "light" | "dark" | "system";
export type ResolvedTheme = "light" | "dark";

export const THEME_STORAGE_KEY = "ats.theme";
export const DEFAULT_THEME_PREFERENCE: ThemePreference = "system";
export const DARK_SCHEME_QUERY = "(prefers-color-scheme: dark)";

const PREFERENCES: readonly string[] = ["light", "dark", "system"];

export function isThemePreference(value: unknown): value is ThemePreference {
  return typeof value === "string" && PREFERENCES.includes(value);
}

/** Stored preference; missing, invalid or unreadable storage means "system". */
export function readThemePreference(): ThemePreference {
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isThemePreference(stored) ? stored : DEFAULT_THEME_PREFERENCE;
  } catch {
    return DEFAULT_THEME_PREFERENCE;
  }
}

/** Best effort: the preference still applies for this visit if storage fails. */
export function writeThemePreference(preference: ThemePreference): void {
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, preference);
  } catch {
    // Storage may be full or blocked.
  }
}

export function systemPrefersDark(): boolean {
  try {
    return window.matchMedia(DARK_SCHEME_QUERY).matches;
  } catch {
    return false;
  }
}

/** Calls `onChange` when the operating system switches between light and dark. */
export function subscribeToSystemTheme(onChange: () => void): () => void {
  let query: MediaQueryList;
  try {
    query = window.matchMedia(DARK_SCHEME_QUERY);
  } catch {
    return () => {};
  }
  query.addEventListener("change", onChange);
  return () => query.removeEventListener("change", onChange);
}

export function resolveTheme(
  preference: ThemePreference,
  systemDark: boolean,
): ResolvedTheme {
  if (preference === "system") {
    return systemDark ? "dark" : "light";
  }
  return preference;
}

/** Same effect as the boot script in index.html. */
export function applyTheme(
  theme: ResolvedTheme,
  root: HTMLElement = document.documentElement,
): void {
  root.classList.toggle("dark", theme === "dark");
  root.style.colorScheme = theme;
}
