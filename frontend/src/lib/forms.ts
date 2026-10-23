import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { ApiError } from "@/lib/api/client";

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

export function errorMessage(error: unknown, fallback = "Ocorreu um erro inesperado. Tente novamente."): string {
  if (error instanceof ApiError) return error.fieldErrors[0]?.message ?? error.message;
  return error instanceof Error ? error.message : fallback;
}

export function safeRedirectPath(value: string | null | undefined, fallback = "/inicio"): string {
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

export function tokenFromHash(): string | undefined {
  if (typeof window === "undefined") return undefined;
  const params = new URLSearchParams(window.location.hash.slice(1));
  return params.get("token") ?? undefined;
}
