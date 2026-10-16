import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { jsonResponse, renderWithProviders } from "@/test/render";
import { AppointmentLinkPage } from "./AppointmentLinkPage";

const appointment = (status: string, canRespond = true) => ({
  clinicName: "Clínica Teste",
  patientFirstName: "Ana",
  professionalName: "Dra. Fictícia",
  startsAt: "2026-11-10T13:00:00Z",
  endsAt: "2026-11-10T13:30:00Z",
  status,
  canRespond,
});

function mockApi(handler: (url: string, body: Record<string, unknown>) => Response) {
  const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    if (url === "/api/v1/auth/csrf") {
      document.cookie = "XSRF-TOKEN=t; path=/";
      return Promise.resolve(new Response(null, { status: 204 }));
    }
    return Promise.resolve(handler(url, init?.body ? JSON.parse(String(init.body)) : {}));
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("AppointmentLinkPage", () => {
  beforeEach(() => {
    window.location.hash = "#token=abc123";
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    window.location.hash = "";
  });

  it("mostra a consulta e confirma a presença enviando o token do link", async () => {
    const fetchMock = mockApi((url) =>
      url.endsWith("/respond") ? jsonResponse(appointment("CONFIRMED", true)) : jsonResponse(appointment("SCHEDULED")),
    );
    renderWithProviders(<AppointmentLinkPage />);

    expect(await screen.findByText("Olá, Ana.")).toBeInTheDocument();
    expect(screen.getByText("Dra. Fictícia")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Confirmar presença" }));

    expect(await screen.findByText("Presença confirmada. Obrigado!")).toBeInTheDocument();
    const respondCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/appointments/respond"));
    expect(JSON.parse(String(respondCall?.[1]?.body))).toEqual({ token: "abc123", action: "CONFIRM" });
  });

  it("não oferece ações quando a consulta não pode mais ser alterada", async () => {
    mockApi(() => jsonResponse(appointment("COMPLETED", false)));
    renderWithProviders(<AppointmentLinkPage />);

    expect(await screen.findByText(/não pode mais ser alterada/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Confirmar/ })).not.toBeInTheDocument();
  });

  it("explica quando o link é inválido", async () => {
    mockApi(() =>
      jsonResponse(
        { title: "Não encontrado", status: 404, detail: "Link inválido ou expirado." },
        404,
        "application/problem+json",
      ),
    );
    renderWithProviders(<AppointmentLinkPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/inválido/);
  });
});
