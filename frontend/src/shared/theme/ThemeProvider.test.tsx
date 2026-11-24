import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { THEME_STORAGE_KEY } from "@/shared/theme/theme";
import { ThemeProvider } from "@/shared/theme/ThemeProvider";
import { useTheme } from "@/shared/theme/useTheme";
import { mockSystemTheme, resetTheme } from "@/test/theme";

function Probe() {
  const { preference, resolvedTheme, setPreference } = useTheme();
  return (
    <div>
      <p>preference: {preference}</p>
      <p>resolved: {resolvedTheme}</p>
      <button type="button" onClick={() => setPreference("light")}>
        light
      </button>
      <button type="button" onClick={() => setPreference("dark")}>
        dark
      </button>
      <button type="button" onClick={() => setPreference("system")}>
        system
      </button>
    </div>
  );
}

function renderProvider() {
  return render(
    <ThemeProvider>
      <Probe />
    </ThemeProvider>,
  );
}

describe("ThemeProvider", () => {
  let system: ReturnType<typeof mockSystemTheme>;
  const root = document.documentElement;

  beforeEach(() => {
    system = mockSystemTheme(false);
  });

  afterEach(() => {
    system.restore();
    resetTheme();
  });

  it("defaults to system and follows a light system", () => {
    renderProvider();

    expect(screen.getByText("preference: system")).toBeInTheDocument();
    expect(screen.getByText("resolved: light")).toBeInTheDocument();
    expect(root).not.toHaveClass("dark");
    expect(root.style.colorScheme).toBe("light");
  });

  it("follows a dark system on start", () => {
    system.setDark(true);
    renderProvider();

    expect(screen.getByText("resolved: dark")).toBeInTheDocument();
    expect(root).toHaveClass("dark");
    expect(root.style.colorScheme).toBe("dark");
  });

  it("starts from the stored preference", () => {
    localStorage.setItem(THEME_STORAGE_KEY, "dark");
    renderProvider();

    expect(screen.getByText("preference: dark")).toBeInTheDocument();
    expect(root).toHaveClass("dark");
  });

  it("applies, removes and persists the chosen theme", async () => {
    const user = userEvent.setup();
    renderProvider();

    await user.click(screen.getByRole("button", { name: "dark" }));
    expect(root).toHaveClass("dark");
    expect(root.style.colorScheme).toBe("dark");
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe("dark");

    await user.click(screen.getByRole("button", { name: "light" }));
    expect(root).not.toHaveClass("dark");
    expect(root.style.colorScheme).toBe("light");
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe("light");

    await user.click(screen.getByRole("button", { name: "system" }));
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe("system");
  });

  it("follows live system changes while on system", () => {
    renderProvider();

    act(() => system.setDark(true));
    expect(screen.getByText("resolved: dark")).toBeInTheDocument();
    expect(root).toHaveClass("dark");

    act(() => system.setDark(false));
    expect(screen.getByText("resolved: light")).toBeInTheDocument();
    expect(root).not.toHaveClass("dark");
  });

  it.each(["light", "dark"] as const)(
    "ignores system changes while on %s",
    async (choice) => {
      const user = userEvent.setup();
      renderProvider();
      await user.click(screen.getByRole("button", { name: choice }));

      act(() => system.setDark(choice === "light"));

      expect(screen.getByText(`resolved: ${choice}`)).toBeInTheDocument();
      expect(root.classList.contains("dark")).toBe(choice === "dark");
    },
  );

  it("stops listening to the system after unmounting", () => {
    const { unmount } = renderProvider();
    expect(system.listenerCount()).toBeGreaterThan(0);

    unmount();

    expect(system.listenerCount()).toBe(0);
  });

  it("throws when useTheme is used outside the provider", () => {
    expect(() => render(<Probe />)).toThrow(/ThemeProvider/);
  });
});
