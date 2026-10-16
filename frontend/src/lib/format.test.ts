import { describe, expect, it } from "vitest";
import {
  ageFrom,
  formatCpf,
  formatDate,
  formatMoney,
  formatPercent,
  formatPhone,
  isValidCpf,
  moneyInWords,
  parseDateOnly,
} from "./format";

describe("formatação brasileira", () => {
  it("formata dinheiro e trata vazio", () => {
    expect(formatMoney(1234.5).replace(/\s/g, " ")).toBe("R$ 1.234,50");
    expect(formatMoney("10")).toContain("10,00");
    expect(formatMoney(null)).toBe("—");
  });

  it("interpreta data sem hora no fuso local, sem voltar um dia", () => {
    expect(formatDate("2026-10-13")).toBe("13/10/2026");
    expect(parseDateOnly("2026-01-01").getDate()).toBe(1);
  });

  it("formata CPF e telefone", () => {
    expect(formatCpf("52998224725")).toBe("529.982.247-25");
    expect(formatPhone("11900000001")).toBe("(11) 90000-0001");
    expect(formatPhone("1133330000")).toBe("(11) 3333-0000");
  });

  it("valida os dígitos verificadores do CPF", () => {
    expect(isValidCpf("529.982.247-25")).toBe(true);
    expect(isValidCpf("529.982.247-24")).toBe(false);
    expect(isValidCpf("111.111.111-11")).toBe(false);
  });

  it("calcula idade em anos completos", () => {
    const today = new Date(2026, 9, 15);
    expect(ageFrom("2000-10-15", today)).toBe(26);
    expect(ageFrom("2000-10-16", today)).toBe(25);
    expect(ageFrom(null, today)).toBeNull();
  });
});

describe("formatPercent", () => {
  it("usa vírgula e no máximo uma casa decimal", () => {
    expect(formatPercent(2.1)).toBe("2,1%");
    expect(formatPercent(45)).toBe("45%");
    expect(formatPercent(33.333)).toBe("33,3%");
    expect(formatPercent(null)).toBe("—");
  });
});

describe("moneyInWords", () => {
  it("escreve valores em reais por extenso", () => {
    expect(moneyInWords(150.3)).toBe("cento e cinquenta reais e trinta centavos");
    expect(moneyInWords(1)).toBe("um real");
    expect(moneyInWords(100)).toBe("cem reais");
    expect(moneyInWords(1250)).toBe("mil duzentos e cinquenta reais");
    expect(moneyInWords(2000)).toBe("dois mil reais");
    expect(moneyInWords(1100)).toBe("mil e cem reais");
    expect(moneyInWords(1050.01)).toBe("mil e cinquenta reais e um centavo");
    expect(moneyInWords(1000000)).toBe("um milhão de reais");
    expect(moneyInWords(0.5)).toBe("cinquenta centavos");
  });
});
