import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { NewTicketForm } from "./NewTicketForm";

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn(), push: vi.fn() }) }));

describe("NewTicketForm", () => {
  it("mostra o que falta embaixo de cada campo e não envia o chamado", async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(null, { status: 401 })));
    vi.stubGlobal("fetch", fetchMock);
    renderWithProviders(<NewTicketForm onClose={() => {}} />);

    await userEvent.setup().click(screen.getByRole("button", { name: "Enviar chamado" }));

    expect(await screen.findByText("Escolha a categoria.")).toBeInTheDocument();
    expect(screen.getByText("Diga o quanto isso atrapalha o trabalho.")).toBeInTheDocument();
    expect(screen.getByText("Informe o assunto.")).toBeInTheDocument();
    expect(screen.getByText("Descreva o que aconteceu.")).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith("/api/v1/support"))).toBe(false);
  });

  it("vindo de um erro, já traz a tela, o código do erro e a mensagem", () => {
    renderWithProviders(
      <NewTicketForm
        report={{ traceId: "trace-42", message: "Erro inesperado", page: "/agenda" }}
        onClose={() => {}}
      />,
    );

    expect(screen.getByText(/Código do erro: trace-42/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Descreva o que aconteceu/)).toHaveValue(
      "O que eu estava fazendo:\n\nO que apareceu na tela: Erro inesperado",
    );
  });
});
