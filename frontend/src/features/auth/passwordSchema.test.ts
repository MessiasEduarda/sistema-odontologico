import { describe, expect, it } from "vitest";
import { passwordProblem } from "./passwordSchema";

describe("passwordProblem", () => {
  it("mostra um problema por vez, na ordem: tamanho, maiúscula, caractere especial, confirmação", () => {
    expect(passwordProblem("ab")).toBe("A senha deve ter no mínimo 8 caracteres.");
    expect(passwordProblem("abcdefgh")).toBe("A senha deve ter pelo menos uma letra maiúscula.");
    expect(passwordProblem("Abcdefgh")).toBe("A senha deve ter pelo menos um caractere especial (ex.: ! @ # $ %).");
    expect(passwordProblem("Abcdefg!", "Abcdefg?")).toBe("As senhas não coincidem.");
    expect(passwordProblem("Abcdefg!", "Abcdefg!")).toBeNull();
    expect(passwordProblem("Ébano#123")).toBeNull();
  });
});
