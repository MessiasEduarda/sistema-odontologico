import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { ApiError } from "@/lib/api/client";

/**
 * Leva os erros de campo vindos do back-end (fieldErrors) para o formulário. Retorna a mensagem geral a exibir
 * quando o erro não é de campo, ou quando o campo não existe no formulário.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): string | undefined {
  if (!(error instanceof ApiError)) return "Ocorreu um erro inesperado. Tente novamente.";
  let unmatched = false;
  for (const fieldError of error.fieldErrors) {
    const field = fields.find((f) => f === fieldError.field);
    if (field) setError(field, { type: "server", message: fieldError.message });
    else unmatched = true;
  }
  return error.fieldErrors.length === 0 || unmatched ? error.message : undefined;
}

/** Aceita apenas caminhos internos (evita redirecionamento aberto para outros sites). */
export function safeRedirectPath(value: string | null | undefined, fallback = "/inicio"): string {
  // Caracteres de controle (TAB, CR, LF) e "\" são removidos ou convertidos pelo navegador ao interpretar a URL:
  // "/\t/site.example" viraria "//site.example". Por isso são recusados, e o destino final é conferido com o parser.
  if (!value || !value.startsWith("/") || value.startsWith("//") || /[\u0000-\u001f\u007f\\]/.test(value)) {
    return fallback;
  }
  try {
    const resolved = new URL(value, "http://interno.invalid");
    if (resolved.origin !== "http://interno.invalid") return fallback;
    return `${resolved.pathname}${resolved.search}${resolved.hash}`;
  } catch {
    return fallback;
  }
}

/** Lê o token do fragmento da URL (#token=...), que não é enviado ao servidor nem fica em logs. */
export function tokenFromHash(): string | undefined {
  if (typeof window === "undefined") return undefined;
  const params = new URLSearchParams(window.location.hash.slice(1));
  return params.get("token") ?? undefined;
}
