/**
 * Cliente HTTP da API. Sempre chama caminhos relativos (/api/...) na mesma origem;
 * o Next.js repassa para o back-end. Erros chegam como Problem Details (RFC 9457).
 *
 * Segurança:
 * - a sessão vive num cookie httpOnly (inacessível a este código);
 * - requisições que alteram estado enviam o token CSRF (cookie XSRF-TOKEN → header X-XSRF-TOKEN).
 */

export interface FieldError {
  field: string;
  message: string;
}

export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  traceId?: string;
  fieldErrors?: FieldError[];
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId?: string;
  readonly fieldErrors: FieldError[];

  constructor(status: number, problem: ProblemDetails) {
    super(problem.detail ?? problem.title ?? "Erro ao comunicar com o servidor.");
    this.name = "ApiError";
    this.status = status;
    this.code = problem.code ?? `HTTP_${status}`;
    this.traceId = problem.traceId;
    this.fieldErrors = problem.fieldErrors ?? [];
  }
}

const NETWORK_ERROR: ProblemDetails = {
  code: "NETWORK_ERROR",
  detail: "Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.",
};

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? "GET").toUpperCase();
  const needsCsrf = !SAFE_METHODS.has(method);

  let response = await send(path, init, needsCsrf ? await csrfToken() : undefined);

  // Token CSRF ausente/expirado: renova uma única vez e repete.
  if (needsCsrf && response.status === 403) {
    const problem = await readProblem(response.clone());
    if (problem.code === "CSRF_INVALID") {
      response = await send(path, init, await csrfToken(true));
    }
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }

  if (response.status === 204 || response.status === 202) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function apiPost<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, jsonInit("POST", body));
}

export function apiPut<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, jsonInit("PUT", body));
}

export function apiDelete<T>(path: string): Promise<T> {
  return apiFetch<T>(path, { method: "DELETE" });
}

function jsonInit(method: string, body: unknown): RequestInit {
  return body === undefined
    ? { method }
    : { method, body: JSON.stringify(body), headers: { "Content-Type": "application/json" } };
}

async function send(path: string, init: RequestInit | undefined, csrf: string | undefined): Promise<Response> {
  const headers: Record<string, string> = { Accept: "application/json", ...(init?.headers as Record<string, string>) };
  if (csrf) headers[CSRF_HEADER] = csrf;
  try {
    return await fetch(path, { ...init, credentials: "same-origin", headers });
  } catch {
    throw new ApiError(0, NETWORK_ERROR);
  }
}

async function csrfToken(forceRefresh = false): Promise<string | undefined> {
  if (!forceRefresh) {
    const existing = readCookie(CSRF_COOKIE);
    if (existing) return existing;
  }
  try {
    await fetch("/api/v1/auth/csrf", { credentials: "same-origin" });
  } catch {
    throw new ApiError(0, NETWORK_ERROR);
  }
  return readCookie(CSRF_COOKIE);
}

function readCookie(name: string): string | undefined {
  if (typeof document === "undefined") return undefined;
  const entry = document.cookie.split("; ").find((c) => c.startsWith(`${name}=`));
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : undefined;
}

async function readProblem(response: Response): Promise<ProblemDetails> {
  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("json")) {
    try {
      return (await response.json()) as ProblemDetails;
    } catch {
      // corpo inválido: cai no padrão abaixo
    }
  }
  return { status: response.status, detail: "O servidor retornou um erro inesperado." };
}
