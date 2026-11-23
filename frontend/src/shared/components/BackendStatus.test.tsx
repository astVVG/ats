import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fetchHealth } from "@/shared/api/health";
import { BackendStatus } from "@/shared/components/BackendStatus";

vi.mock("@/shared/api/health", () => ({ fetchHealth: vi.fn() }));

const fetchHealthMock = vi.mocked(fetchHealth);

function renderStatus() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <BackendStatus />
    </QueryClientProvider>,
  );
}

describe("BackendStatus", () => {
  beforeEach(() => {
    fetchHealthMock.mockReset();
  });

  it("shows the loading state while the request is pending", () => {
    fetchHealthMock.mockReturnValue(new Promise(() => {}));
    renderStatus();
    expect(screen.getByText("Verificando backend…")).toBeInTheDocument();
  });

  it("shows the backend as available when status is UP", async () => {
    fetchHealthMock.mockResolvedValue({ status: "UP" });
    renderStatus();
    expect(await screen.findByText("Backend disponible")).toBeInTheDocument();
  });

  it("shows the backend as unavailable when the request fails", async () => {
    fetchHealthMock.mockRejectedValue(new Error("Network Error"));
    renderStatus();
    expect(
      await screen.findByText("Backend no disponible"),
    ).toBeInTheDocument();
  });

  it("shows the backend as unavailable when status is not UP", async () => {
    fetchHealthMock.mockResolvedValue({ status: "DOWN" });
    renderStatus();
    expect(
      await screen.findByText("Backend no disponible"),
    ).toBeInTheDocument();
  });
});
