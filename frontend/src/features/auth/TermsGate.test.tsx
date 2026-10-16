import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { jsonResponse, renderWithProviders } from "@/test/render";
import { TermsGate } from "./TermsGate";

const me = (termsAccepted: boolean) => ({
  user: { id: "u1", name: "Pessoa", email: "p@teste.example" },
  platformAdmin: false,
  activeClinic: { id: "c1", name: "Clínica 1", slug: "c1" },
  permissions: [],
  clinics: [{ id: "c1", name: "Clínica 1", slug: "c1" }],
  termsAccepted,
  termsVersion: "2026-10-15",
});

function mockApi(accepted: boolean) {
  const fetchMock = vi.fn().mockImplementation((url: string) => {
    if (url === "/api/v1/auth/csrf") {
      document.cookie = "XSRF-TOKEN=t; path=/";
      return Promise.resolve(new Response(null, { status: 204 }));
    }
    if (url === "/api/v1/auth/terms/accept") return Promise.resolve(jsonResponse(me(true)));
    return Promise.resolve(jsonResponse(me(accepted)));
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("TermsGate", () => {
  beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
  afterEach(() => vi.useRealTimers());

  it("aparece após 3 segundos e só libera o botão depois de marcar o aceite", async () => {
    const fetchMock = mockApi(false);
    renderWithProviders(<TermsGate />);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/v1/auth/me", expect.anything()));

    act(() => vi.advanceTimersByTime(2000));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    act(() => vi.advanceTimersByTime(1000));
    expect(await screen.findByRole("dialog", { name: "Termos de Uso e Privacidade" })).toBeInTheDocument();

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const button = screen.getByRole("button", { name: "Aceitar e continuar" });
    expect(button).toBeDisabled();

    await user.click(screen.getByRole("tab", { name: "Política de Privacidade" }));
    expect(screen.getByRole("tab", { name: "Política de Privacidade" })).toHaveAttribute("aria-selected", "true");

    await user.click(screen.getByLabelText("Li e aceito os Termos de Uso e a Política de Privacidade"));
    expect(button).toBeEnabled();
    await user.click(button);

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    const acceptCall = fetchMock.mock.calls.find(([url]) => url === "/api/v1/auth/terms/accept");
    expect(JSON.parse(acceptCall?.[1].body)).toEqual({ version: "2026-10-15" });
  });

  it("não aparece para quem já aceitou", async () => {
    const fetchMock = mockApi(true);
    renderWithProviders(<TermsGate />);
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    act(() => vi.advanceTimersByTime(5000));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
