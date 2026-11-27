import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getFunnel,
  getTimeInStage,
  getTimeToHire,
} from "@/features/metrics/api";
import {
  EMPTY_FILTERS,
  isRangeInverted,
  toMetricsFilters,
} from "@/features/metrics/types";
import { apiClient } from "@/shared/api/client";

describe("metrics api", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it.each([
    ["/metrics/funnel", getFunnel],
    ["/metrics/time-in-stage", getTimeInStage],
    ["/metrics/time-to-hire", getTimeToHire],
  ] as const)("calls %s with the given filters", async (url, call) => {
    const get = vi
      .spyOn(apiClient, "get")
      .mockResolvedValue({ data: { ok: true } });

    await expect(
      call({ jobId: 3, from: "2026-01-15", to: "2026-01-20" }),
    ).resolves.toEqual({ ok: true });
    expect(get).toHaveBeenCalledWith(url, {
      params: { jobId: 3, from: "2026-01-15", to: "2026-01-20" },
    });

    await call({});
    expect(get).toHaveBeenLastCalledWith(url, { params: {} });
  });
});

describe("toMetricsFilters", () => {
  it("sends nothing without filters", () => {
    expect(toMetricsFilters(EMPTY_FILTERS)).toEqual({});
  });

  it("keeps the date strings as they are and the job as a number", () => {
    expect(
      toMetricsFilters({ job: "7", from: "2026-01-15", to: "2026-01-20" }),
    ).toEqual({ jobId: 7, from: "2026-01-15", to: "2026-01-20" });
  });

  it("sends only the dates that are set", () => {
    expect(toMetricsFilters({ ...EMPTY_FILTERS, from: "2026-03-01" })).toEqual({
      from: "2026-03-01",
    });
    expect(toMetricsFilters({ ...EMPTY_FILTERS, to: "2026-03-01" })).toEqual({
      to: "2026-03-01",
    });
  });

  it("blocks an inverted range but accepts the same day", () => {
    const inverted = { ...EMPTY_FILTERS, from: "2026-02-01", to: "2026-01-31" };
    expect(isRangeInverted(inverted)).toBe(true);
    expect(toMetricsFilters(inverted)).toBeNull();
    expect(
      toMetricsFilters({
        ...EMPTY_FILTERS,
        from: "2026-02-01",
        to: "2026-02-01",
      }),
    ).toEqual({ from: "2026-02-01", to: "2026-02-01" });
  });
});
