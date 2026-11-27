import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createApplication,
  getJobBoard,
  moveApplication,
} from "@/features/board/api";
import { apiClient } from "@/shared/api/client";
import { buildBoard } from "@/test/utils";

describe("board api", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("gets the board of a job", async () => {
    const board = buildBoard();
    const get = vi.spyOn(apiClient, "get").mockResolvedValue({ data: board });

    await expect(getJobBoard(7)).resolves.toEqual(board);
    expect(get).toHaveBeenCalledWith("/jobs/7/board");
  });

  it("moves an application with stage and position", async () => {
    const patch = vi
      .spyOn(apiClient, "patch")
      .mockResolvedValue({ data: { id: 3 } });

    await expect(
      moveApplication(3, { stage: "OFFER", position: 1 }),
    ).resolves.toEqual({ id: 3 });
    expect(patch).toHaveBeenCalledWith("/applications/3/move", {
      stage: "OFFER",
      position: 1,
    });
  });

  it("creates an application for a job and a candidate", async () => {
    const post = vi
      .spyOn(apiClient, "post")
      .mockResolvedValue({ data: { id: 9, stage: "APPLIED" } });

    await expect(
      createApplication({ jobId: 10, candidateId: 20 }),
    ).resolves.toEqual({ id: 9, stage: "APPLIED" });
    expect(post).toHaveBeenCalledWith("/applications", {
      jobId: 10,
      candidateId: 20,
    });
  });
});
