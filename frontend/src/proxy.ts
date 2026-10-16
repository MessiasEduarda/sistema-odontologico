import { NextResponse, type NextRequest } from "next/server";

const SESSION_COOKIE = "odonto_session";
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

/**
 * Redireciona para o login quem não tem cookie de sessão. É só uma conveniência de navegação:
 * a validação real da sessão e das permissões acontece na API em toda requisição.
 */
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const isPublic = PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
  if (isPublic || request.cookies.has(SESSION_COOKIE)) return NextResponse.next();

  const login = new URL("/entrar", request.url);
  login.searchParams.set("next", `${pathname}${search}`);
  return NextResponse.redirect(login);
}

export const config = {
  // Ignora a API (proxy para o back-end), arquivos internos do Next e estáticos.
  matcher: ["/((?!api/|_next/|favicon.ico|.*\.(?:svg|png|jpg|jpeg|webp|ico|txt|otf|ttf|woff|woff2)$).*)"],
};
