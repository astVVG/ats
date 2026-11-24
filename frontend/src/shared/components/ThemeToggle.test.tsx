import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { ThemeToggle } from "@/shared/components/ThemeToggle";
import { THEME_STORAGE_KEY } from "@/shared/theme/theme";
import { ThemeProvider } from "@/shared/theme/ThemeProvider";
import { mockSystemTheme, resetTheme } from "@/test/theme";

function renderToggle() {
  render(
    <ThemeProvider>
      <ThemeToggle />
    </ThemeProvider>,
  );
  return within(screen.getByRole("group", { name: "Tema" }));
}

describe("ThemeToggle", () => {
  let system: ReturnType<typeof mockSystemTheme>;
  const root = document.documentElement;

  beforeEach(() => {
    system = mockSystemTheme(false);
  });

  afterEach(() => {
    system.restore();
    resetTheme();
  });

  it("shows three labelled buttons with system pressed by default", () => {
    const group = renderToggle();

    const buttons = group.getAllByRole("button");
    expect(buttons.map((b) => b.getAttribute("aria-label"))).toEqual([
      "Tema claro",
      "Tema oscuro",
      "Tema del sistema",
    ]);
    expect(
      group.getByRole("button", { name: "Tema del sistema" }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(group.getByRole("button", { name: "Tema claro" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    expect(group.getByRole("button", { name: "Tema oscuro" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("switches the theme, the pressed button and the stored value", async () => {
    const user = userEvent.setup();
    const group = renderToggle();

    await user.click(group.getByRole("button", { name: "Tema oscuro" }));
    expect(root).toHaveClass("dark");
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe("dark");
    expect(group.getByRole("button", { name: "Tema oscuro" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(
      group.getByRole("button", { name: "Tema del sistema" }),
    ).toHaveAttribute("aria-pressed", "false");

    await user.click(group.getByRole("button", { name: "Tema claro" }));
    expect(root).not.toHaveClass("dark");
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe("light");

    system.setDark(true);
    await user.click(group.getByRole("button", { name: "Tema del sistema" }));
    expect(root).toHaveClass("dark");
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe("system");
  });
});
