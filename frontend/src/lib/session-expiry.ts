import { ApiError } from "@/lib/api/client";

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

export function isSessionExpired(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401 && error.code === "UNAUTHENTICATED";
}

export function redirectToLogin(onClear: () => void): void {
  if (typeof window === "undefined" || redirecting || isPublicPath(window.location.pathname)) return;
  redirecting = true;
  onClear();
  const next = `${window.location.pathname}${window.location.search}`;
  window.location.replace(`/entrar?next=${encodeURIComponent(next)}`);
}
