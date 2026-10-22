import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/client";
import { isPublicPath, isSessionExpired } from "./session-expiry";

describe("sessão expirada", () => {
  it("reconhece 401 UNAUTHENTICATED como sessão expirada", () => {
    expect(isSessionExpired(new ApiError(401, { code: "UNAUTHENTICATED" }))).toBe(true);
  });

  it("não trata senha errada no login nem outros erros como sessão expirada", () => {
    expect(isSessionExpired(new ApiError(401, { code: "INVALID_CREDENTIALS" }))).toBe(false);
    expect(isSessionExpired(new ApiError(403, { code: "FORBIDDEN" }))).toBe(false);
    expect(isSessionExpired(new ApiError(0, { code: "NETWORK_ERROR" }))).toBe(false);
    expect(isSessionExpired(new Error("falha"))).toBe(false);
  });

  it("não redireciona de páginas públicas", () => {
    expect(isPublicPath("/entrar")).toBe(true);
    expect(isPublicPath("/portal")).toBe(true);
    expect(isPublicPath("/termo/abc")).toBe(true);
    expect(isPublicPath("/pacientes")).toBe(false);
    expect(isPublicPath("/entrarx")).toBe(false);
  });
});
