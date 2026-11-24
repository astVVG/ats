import { DARK_SCHEME_QUERY, THEME_STORAGE_KEY } from "@/shared/theme/theme";

/**
 * Replaces window.matchMedia with one whose dark-scheme query is controlled by
 * the test. Call restore() afterwards.
 */
export function mockSystemTheme(initiallyDark: boolean) {
  const original = window.matchMedia;
  let dark = initiallyDark;
  const listeners = new Set<EventListenerOrEventListenerObject>();

  window.matchMedia = (query: string): MediaQueryList => ({
    get matches() {
      return query === DARK_SCHEME_QUERY && dark;
    },
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: (
      _type: string,
      listener: EventListenerOrEventListenerObject,
    ) => {
      listeners.add(listener);
    },
    removeEventListener: (
      _type: string,
      listener: EventListenerOrEventListenerObject,
    ) => {
      listeners.delete(listener);
    },
    dispatchEvent: () => false,
  });

  return {
    setDark(next: boolean) {
      dark = next;
      const event = new Event("change");
      for (const listener of listeners) {
        if (typeof listener === "function") {
          listener(event);
        } else {
          listener.handleEvent(event);
        }
      }
    },
    listenerCount: () => listeners.size,
    restore() {
      window.matchMedia = original;
    },
  };
}

/** Removes whatever a previous test left on <html> and in storage. */
export function resetTheme() {
  document.documentElement.classList.remove("dark");
  document.documentElement.style.colorScheme = "";
  localStorage.removeItem(THEME_STORAGE_KEY);
}
