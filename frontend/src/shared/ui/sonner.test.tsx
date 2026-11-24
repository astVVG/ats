import { act, render, screen } from "@testing-library/react";
import { toast } from "sonner";
import { afterEach, describe, expect, it } from "vitest";
import { THEME_STORAGE_KEY } from "@/shared/theme/theme";
import { ThemeProvider } from "@/shared/theme/ThemeProvider";
import { Toaster } from "@/shared/ui/sonner";
import { mockSystemTheme, resetTheme } from "@/test/theme";

describe("Toaster", () => {
  afterEach(() => {
    act(() => {
      toast.dismiss();
    });
    resetTheme();
  });

  it.each([
    ["dark", false, "dark"],
    ["light", true, "light"],
    ["system", true, "dark"],
  ] as const)(
    "uses the resolved theme for %s with system dark=%s",
    async (preference, systemDark, expected) => {
      const system = mockSystemTheme(systemDark);
      try {
        localStorage.setItem(THEME_STORAGE_KEY, preference);
        render(
          <ThemeProvider>
            <Toaster />
          </ThemeProvider>,
        );

        act(() => {
          toast("Guardado");
        });

        const message = await screen.findByText("Guardado");
        expect(message.closest("[data-sonner-theme]")).toHaveAttribute(
          "data-sonner-theme",
          expected,
        );
      } finally {
        system.restore();
      }
    },
  );
});
