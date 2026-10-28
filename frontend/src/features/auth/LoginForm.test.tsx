import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { jsonResponse, renderWithProviders } from "@/test/render";
import { LoginForm } from "./LoginForm";

const replace = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace, push: vi.fn() }) }));

const me = (clinics: number, active: boolean) => ({
  user: { id: "u1", name: "Pessoa", email: "p@teste.example" },
  platformAdmin: false,
  activeClinic: active ? { id: "c1", name: "Clínica 1", slug: "c1" } : null,
  permissions: [],
  clinics: Array.from({ length: clinics }, (_, i) => ({
    id: `c${i + 1}`,
    name: `Clínica ${i + 1}`,
    slug: `c${i + 1}`,
  })),
});

const CONTACT_URL = "/api/v1/public/support/contact";
const contactResponse = (email: string | null) => jsonResponse({ email, hours: "segunda a sexta, das 8h às 18h" });

function mockApi(loginResponse: Response, supportEmail: string | null = null) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockImplementation((url: string) => {
      if (url === CONTACT_URL) return Promise.resolve(contactResponse(supportEmail));
      if (url === "/api/v1/auth/csrf") {
        document.cookie = "XSRF-TOKEN=t; path=/";
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(loginResponse);
    }),
  );
}

async function fillAndSubmit(email = "p@teste.example", password = "Senha-Forte-123") {
  const user = userEvent.setup();
  if (email) await user.type(screen.getByLabelText("E-mail"), email);
  if (password) await user.type(screen.getByLabelText("Senha"), password);
  await user.click(screen.getByRole("button", { name: "Acessar" }));
}

describe("LoginForm", () => {
  beforeEach(() => {
    replace.mockReset();
    window.history.replaceState(null, "", "/entrar");
  });

  it("valida os campos antes de chamar a API", async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(contactResponse(null)));
    vi.stubGlobal("fetch", fetchMock);
    renderWithProviders(<LoginForm />);

    await fillAndSubmit("nao-e-email", "");

    expect(await screen.findByRole("alert")).toHaveTextContent("Informe o e-mail e a senha.");
    expect(screen.getAllByRole("alert")).toHaveLength(1);
    expect(fetchMock.mock.calls.every(([url]) => url === CONTACT_URL)).toBe(true);
  });

  it("mostra o link de e-mail do suporte para quem não consegue entrar", async () => {
    mockApi(jsonResponse(me(1, true)), "suporte@teste.example");
    renderWithProviders(<LoginForm />);

    expect(await screen.findByRole("link", { name: "Falar com o suporte" })).toHaveAttribute(
      "href",
      "mailto:suporte@teste.example",
    );
  });

  it("mostra a mensagem do servidor quando as credenciais são inválidas", async () => {
    mockApi(
      jsonResponse(
        { code: "INVALID_CREDENTIALS", detail: "E-mail ou senha inválidos." },
        401,
        "application/problem+json",
      ),
    );
    renderWithProviders(<LoginForm />);

    await fillAndSubmit();

    expect(await screen.findByRole("alert")).toHaveTextContent("E-mail ou senha inválidos.");
    expect(replace).not.toHaveBeenCalled();
  });

  it("com uma clínica, vai direto para o destino solicitado", async () => {
    window.history.replaceState(null, "", "/entrar?next=%2Fagenda");
    mockApi(jsonResponse(me(1, true)));
    renderWithProviders(<LoginForm />);

    await fillAndSubmit();

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith("/agenda"));
  });

  it("ignora destino externo malicioso", async () => {
    window.history.replaceState(null, "", "/entrar?next=https%3A%2F%2Fmalicioso.example");
    mockApi(jsonResponse(me(1, true)));
    renderWithProviders(<LoginForm />);

    await fillAndSubmit();

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith("/inicio"));
  });

  it("com várias clínicas e nenhuma ativa, pede para escolher", async () => {
    mockApi(jsonResponse(me(2, false)));
    renderWithProviders(<LoginForm />);

    await fillAndSubmit();

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith("/selecionar-clinica"));
  });
});
