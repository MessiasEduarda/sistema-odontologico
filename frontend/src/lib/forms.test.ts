import { describe, expect, it } from "vitest";
import { safeRedirectPath } from "./forms";

describe("safeRedirectPath", () => {
  it.each([
    ["/agenda", "/agenda"],
    ["/pacientes?busca=ana", "/pacientes?busca=ana"],
  ])("aceita caminho interno %s", (input, expected) => {
    expect(safeRedirectPath(input)).toBe(expected);
  });

  it.each([
    "https://site-malicioso.example",
    "//site-malicioso.example",
    "/\\site-malicioso.example",
    "/\t/site-malicioso.example",
    "/\n/site-malicioso.example",
    "/\r/site-malicioso.example",
    "javascript:alert(1)",
    "",
    null,
  ])("rejeita destino externo ou inválido %s", (input) => {
    expect(safeRedirectPath(input)).toBe("/inicio");
  });
});
