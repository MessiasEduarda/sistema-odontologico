import { ApiError } from "@/lib/api/client";

/** Páginas que funcionam sem sessão: um 401 nelas (por exemplo, senha errada no login) não é sessão expirada. */
const PUBLIC_PATHS = [
  "/entrar",
  "/esqueci-senha",
  "/redefinir-senha",
  "/convite",
  "/consulta",
  "/termo",
  "/portal",
  "/termos-de-uso",
  "/privacidade",
];

let redirecting = false;

export function isPublicPath(pathname: string): boolean {
  return PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

/** True quando o erro é "sem sessão" (cookie expirado ou revogado), e não falha de credenciais no login. */
export function isSessionExpired(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401 && error.code === "UNAUTHENTICATED";
}

/**
 * Sessão expirada ou revogada durante o uso: descarta o que está em memória (dados de saúde em cache) e volta
 * ao login, retornando à página atual depois de entrar. Navegação completa de propósito: zera todo o estado do app.
 */
export function redirectToLogin(onClear: () => void): void {
  if (typeof window === "undefined" || redirecting || isPublicPath(window.location.pathname)) return;
  redirecting = true;
  onClear();
  const next = `${window.location.pathname}${window.location.search}`;
  window.location.replace(`/entrar?next=${encodeURIComponent(next)}`);
}
