import { afterEach, describe, expect, it, vi } from "vitest";
import { jsonResponse } from "@/test/render";
import { ApiError, apiFetch, apiPost } from "./client";

describe("apiFetch", () => {
  it("retorna o corpo JSON em respostas de sucesso", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ ok: true })));
    await expect(apiFetch("/api/x")).resolves.toEqual({ ok: true });
  });

  it("envia cookies apenas para a mesma origem", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}));
    vi.stubGlobal("fetch", fetchMock);
    await apiFetch("/api/x");
    expect(fetchMock).toHaveBeenCalledWith("/api/x", expect.objectContaining({ credentials: "same-origin" }));
  });

  it("converte Problem Details em ApiError com código, traceId e erros de campo", async () => {
    const problem = {
      status: 400,
      code: "VALIDATION_ERROR",
      detail: "Um ou mais campos não passaram na validação.",
      traceId: "trace-123",
      fieldErrors: [{ field: "name", message: "obrigatório" }],
    };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(problem, 400, "application/problem+json")));

    const error = await apiFetch("/api/x").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: "VALIDATION_ERROR",
      traceId: "trace-123",
      message: problem.detail,
      fieldErrors: problem.fieldErrors,
    });
  });

  it("gera erro genérico quando o corpo de erro não é JSON", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("Bad gateway", { status: 502 })));
    await expect(apiFetch("/api/x")).rejects.toMatchObject({ status: 502, code: "HTTP_502" });
  });

  it("informa falha de rede de forma amigável", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    await expect(apiFetch("/api/x")).rejects.toMatchObject({ status: 0, code: "NETWORK_ERROR" });
  });
});

describe("apiFetch — CSRF", () => {
  afterEach(() => {
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
  });

  it("não envia token CSRF em GET", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}));
    vi.stubGlobal("fetch", fetchMock);
    await apiFetch("/api/x");
    expect(fetchMock.mock.calls[0]?.[1]?.headers).not.toHaveProperty("X-XSRF-TOKEN");
  });

  it("busca o cookie CSRF antes do primeiro POST e envia no header", async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url === "/api/v1/auth/csrf") {
        document.cookie = "XSRF-TOKEN=abc123; path=/";
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(jsonResponse({ ok: true }));
    });
    vi.stubGlobal("fetch", fetchMock);

    await apiPost("/api/x", { a: 1 });

    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/v1/auth/csrf");
    expect(fetchMock.mock.calls[1]?.[1]?.headers).toMatchObject({
      "X-XSRF-TOKEN": "abc123",
      "Content-Type": "application/json",
    });
  });

  it("renova o token e repete uma vez quando o servidor recusa o CSRF", async () => {
    document.cookie = "XSRF-TOKEN=velho; path=/";
    let attempts = 0;
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url === "/api/v1/auth/csrf") {
        document.cookie = "XSRF-TOKEN=novo; path=/";
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      attempts += 1;
      return Promise.resolve(
        attempts === 1
          ? jsonResponse({ code: "CSRF_INVALID", detail: "x" }, 403, "application/problem+json")
          : jsonResponse({ ok: true }),
      );
    });
    vi.stubGlobal("fetch", fetchMock);

    await expect(apiPost("/api/x", {})).resolves.toEqual({ ok: true });
    expect(fetchMock.mock.calls.at(-1)?.[1]?.headers).toMatchObject({ "X-XSRF-TOKEN": "novo" });
  });

  it("não repete em 403 de permissão", async () => {
    document.cookie = "XSRF-TOKEN=ok; path=/";
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ code: "FORBIDDEN", detail: "Sem permissão" }, 403, "application/problem+json"));
    vi.stubGlobal("fetch", fetchMock);

    await expect(apiPost("/api/x", {})).rejects.toMatchObject({ status: 403, code: "FORBIDDEN" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
