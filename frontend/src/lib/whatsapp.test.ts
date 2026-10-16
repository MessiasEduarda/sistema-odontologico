import { describe, expect, it } from "vitest";
import { firstName, whatsappLink, whatsappNumber } from "./whatsapp";

describe("whatsapp", () => {
  it("normaliza celulares brasileiros", () => {
    expect(whatsappNumber("(11) 98888-7777")).toBe("5511988887777");
    expect(whatsappNumber("+55 11 98888-7777")).toBe("5511988887777");
    expect(whatsappNumber("1133334444")).toBe("551133334444");
    expect(whatsappNumber("123")).toBeNull();
    expect(whatsappNumber(null)).toBeNull();
  });

  it("monta o link com a mensagem codificada", () => {
    expect(whatsappLink("(11) 98888-7777", "Olá, Ana! Até amanhã?")).toBe(
      "https://wa.me/5511988887777?text=Ol%C3%A1%2C%20Ana!%20At%C3%A9%20amanh%C3%A3%3F",
    );
    expect(whatsappLink("1", "x")).toBeNull();
  });

  it("pega o primeiro nome", () => {
    expect(firstName("  Ana Maria Souza ")).toBe("Ana");
  });
});
