import { afterEach, describe, expect, it, vi } from "vitest";
import {
  applyTheme,
  isThemePreference,
  readThemePreference,
  resolveTheme,
  systemPrefersDark,
  THEME_STORAGE_KEY,
  writeThemePreference,
} from "@/shared/theme/theme";
import { mockSystemTheme, resetTheme } from "@/test/theme";

describe("theme", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    resetTheme();
  });

  it.each([
    ["light", false, "light"],
    ["light", true, "light"],
    ["dark", false, "dark"],
    ["dark", true, "dark"],
    ["system", false, "light"],
    ["system", true, "dark"],
  ] as const)("resolves %s with system dark=%s to %s", (pref, dark, theme) => {
    expect(resolveTheme(pref, dark)).toBe(theme);
  });

  it("recognizes only the three preferences", () => {
    expect(isThemePreference("light")).toBe(true);
    expect(isThemePreference("dark")).toBe(true);
    expect(isThemePreference("system")).toBe(true);
    expect(isThemePreference("Dark")).toBe(false);
    expect(isThemePreference("")).toBe(false);
    expect(isThemePreference(null)).toBe(false);
  });

  it("reads system when nothing is stored", () => {
    expect(readThemePreference()).toBe("system");
  });

  it("reads a valid stored preference", () => {
    localStorage.setItem(THEME_STORAGE_KEY, "dark");
    expect(readThemePreference()).toBe("dark");
  });

  it.each(["purple", "", "DARK", '"dark"'])(
    "treats the invalid stored value %j as system",
    (value) => {
      localStorage.setItem(THEME_STORAGE_KEY, value);
      expect(readThemePreference()).toBe("system");
    },
  );

  it("reads system when storage throws", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(readThemePreference()).toBe("system");
  });

  it("writes the preference and survives a storage that throws", () => {
    writeThemePreference("light");
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe("light");

    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("quota");
    });
    expect(() => writeThemePreference("dark")).not.toThrow();
  });

  it("reads the system theme and falls back to light when matchMedia fails", () => {
    const system = mockSystemTheme(true);
    try {
      expect(systemPrefersDark()).toBe(true);
      system.setDark(false);
      expect(systemPrefersDark()).toBe(false);
    } finally {
      system.restore();
    }

    vi.spyOn(window, "matchMedia").mockImplementation(() => {
      throw new Error("unsupported");
    });
    expect(systemPrefersDark()).toBe(false);
  });

  it("applies the dark class and color-scheme to <html>", () => {
    const root = document.documentElement;

    applyTheme("dark");
    expect(root).toHaveClass("dark");
    expect(root.style.colorScheme).toBe("dark");

    applyTheme("light");
    expect(root).not.toHaveClass("dark");
    expect(root.style.colorScheme).toBe("light");
  });
});
