import { AxiosError, AxiosHeaders, type AxiosResponse } from "axios";
import { describe, expect, it } from "vitest";
import { apiClient } from "@/shared/api/client";
import { getProblemDetail, type ProblemDetail } from "@/shared/api/errors";

function axiosErrorWith(data: unknown, status = 400): AxiosError {
  const config = { headers: new AxiosHeaders() };
  const response: AxiosResponse = {
    data,
    status,
    statusText: "",
    headers: {},
    config,
  };
  return new AxiosError(
    "Request failed",
    "ERR_BAD_REQUEST",
    config,
    null,
    response,
  );
}

describe("apiClient", () => {
  it("uses /api as base URL", () => {
    expect(apiClient.defaults.baseURL).toBe("/api");
  });
});

describe("getProblemDetail", () => {
  it("returns the problem detail from an Axios error", () => {
    const problem: ProblemDetail = {
      type: "about:blank",
      title: "Validation failed",
      status: 400,
      detail: "One or more fields are invalid",
      instance: "/api/jobs",
      errors: [{ field: "title", message: "must not be blank" }],
    };

    expect(getProblemDetail(axiosErrorWith(problem))).toEqual(problem);
  });

  it("returns null for a plain Error", () => {
    expect(getProblemDetail(new Error("boom"))).toBeNull();
  });

  it("returns null for an Axios error without body", () => {
    expect(
      getProblemDetail(new AxiosError("Network Error", "ERR_NETWORK")),
    ).toBeNull();
    expect(getProblemDetail(axiosErrorWith(undefined, 500))).toBeNull();
  });
});
