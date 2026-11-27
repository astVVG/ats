import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createApplication,
  createNote,
  deleteNote,
  getJobBoard,
  listHistory,
  listNotes,
  moveApplication,
  rateApplication,
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

  it("lists the notes of an application 20 at a time", async () => {
    const get = vi
      .spyOn(apiClient, "get")
      .mockResolvedValue({ data: { content: [] } });

    await expect(listNotes(4, 2)).resolves.toEqual({ content: [] });
    expect(get).toHaveBeenCalledWith("/applications/4/notes", {
      params: { page: 2, size: 20 },
    });
  });

  it("creates a note", async () => {
    const post = vi
      .spyOn(apiClient, "post")
      .mockResolvedValue({ data: { id: 8 } });

    await expect(createNote(4, { content: "Hola" })).resolves.toEqual({
      id: 8,
    });
    expect(post).toHaveBeenCalledWith("/applications/4/notes", {
      content: "Hola",
    });
  });

  it("deletes a note", async () => {
    const remove = vi
      .spyOn(apiClient, "delete")
      .mockResolvedValue({ data: "" });

    await expect(deleteNote(8)).resolves.toBeUndefined();
    expect(remove).toHaveBeenCalledWith("/notes/8");
  });

  it("lists the stage history of an application 20 at a time", async () => {
    const get = vi
      .spyOn(apiClient, "get")
      .mockResolvedValue({ data: { content: [] } });

    await expect(listHistory(4, 0)).resolves.toEqual({ content: [] });
    expect(get).toHaveBeenCalledWith("/applications/4/history", {
      params: { page: 0, size: 20 },
    });
  });

  it("rates an application, with null to remove the rating", async () => {
    const patch = vi
      .spyOn(apiClient, "patch")
      .mockResolvedValue({ data: { id: 4, rating: null } });

    await expect(rateApplication(4, { rating: null })).resolves.toEqual({
      id: 4,
      rating: null,
    });
    expect(patch).toHaveBeenCalledWith("/applications/4/rating", {
      rating: null,
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
