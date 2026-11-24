import { afterEach, describe, expect, it, vi } from "vitest";
import indexHtml from "../../index.html?raw";
import { THEME_STORAGE_KEY } from "@/shared/theme/theme";
import { mockSystemTheme, resetTheme } from "@/test/theme";

const page = new DOMParser().parseFromString(indexHtml, "text/html");
const inlineScripts = [...page.head.querySelectorAll("script")].filter(
  (script) => !script.src,
);

function runBootScript() {
  new Function(inlineScripts[0].textContent ?? "")();
}

describe("index.html theme boot script", () => {
  const root = document.documentElement;

  afterEach(() => {
    vi.restoreAllMocks();
    resetTheme();
  });

  it("is a single inline classic script in <head>, before the bundle", () => {
    expect(inlineScripts).toHaveLength(1);
    expect(inlineScripts[0].type).toBe("");
    expect(page.head.querySelector('script[type="module"]')).toBeNull();
    expect(indexHtml.indexOf("ats.theme")).toBeLessThan(
      indexHtml.indexOf("/src/main.tsx"),
    );
  });

  it.each([
    // stored, system dark, expected
    ["dark", false, "dark"],
    ["dark", true, "dark"],
    ["light", true, "light"],
    ["light", false, "light"],
    ["system", true, "dark"],
    ["system", false, "light"],
    [null, true, "dark"],
    [null, false, "light"],
    ["purple", true, "dark"],
    ["purple", false, "light"],
  ] as const)(
    "with %j stored and system dark=%s applies %s",
    (stored, systemDark, expected) => {
      if (stored !== null) {
        localStorage.setItem(THEME_STORAGE_KEY, stored);
      }
      const system = mockSystemTheme(systemDark);
      try {
        runBootScript();
      } finally {
        system.restore();
      }

      expect(root.classList.contains("dark")).toBe(expected === "dark");
      expect(root.style.colorScheme).toBe(expected);
    },
  );

  it("removes a dark class left over when the theme is light", () => {
    root.classList.add("dark");
    localStorage.setItem(THEME_STORAGE_KEY, "light");

    runBootScript();

    expect(root).not.toHaveClass("dark");
  });

  it("falls back to light without throwing when storage is blocked", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });

    expect(runBootScript).not.toThrow();
    expect(root).not.toHaveClass("dark");
    expect(root.style.colorScheme).toBe("light");
  });

  it("still honours a stored dark theme when matchMedia is missing", () => {
    localStorage.setItem(THEME_STORAGE_KEY, "dark");
    vi.spyOn(window, "matchMedia").mockImplementation(() => {
      throw new Error("unsupported");
    });

    runBootScript();

    expect(root).toHaveClass("dark");
  });
});
