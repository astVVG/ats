import { createContext } from "react";
import type { ResolvedTheme, ThemePreference } from "@/shared/theme/theme";

export type ThemeContextValue = {
  /** What the user chose. */
  preference: ThemePreference;
  /** What is shown: the preference, or the system theme for "system". */
  resolvedTheme: ResolvedTheme;
  setPreference: (preference: ThemePreference) => void;
};

export const ThemeContext = createContext<ThemeContextValue | null>(null);
