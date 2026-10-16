import { describe, expect, it } from "vitest";
import { normalizeHeader, parseCsv, readCsvFile, toCsv } from "./csv";

describe("csv", () => {
  it("lê o formato do Excel brasileiro (;) com aspas e quebra de linha", () => {
    const text = 'Nome;Observações\r\n"Ana; Maria";"linha 1\nlinha 2"\r\n;\r\nBruno;"diz ""oi"""\r\n';
    expect(parseCsv(text)).toEqual([
      ["Nome", "Observações"],
      ["Ana; Maria", "linha 1\nlinha 2"],
      ["Bruno", 'diz "oi"'],
    ]);
  });

  it("detecta vírgula como separador", () => {
    expect(parseCsv("a,b\n1,2")).toEqual([
      ["a", "b"],
      ["1", "2"],
    ]);
  });

  it("normaliza cabeçalhos com acento e pontuação", () => {
    expect(normalizeHeader("  Data de Nascimento ")).toBe("data de nascimento");
    expect(normalizeHeader("Número do convênio")).toBe("numero do convenio");
    expect(normalizeHeader("E-mail")).toBe("e mail");
  });

  it("lê arquivo em Windows-1252 (acentos do Excel)", async () => {
    const bytes = new Uint8Array([0x4a, 0x6f, 0xe3, 0x6f]); // "João" em Windows-1252
    expect(await readCsvFile(new Blob([bytes]))).toBe("João");
  });

  it("gera CSV com BOM e escapa separadores", () => {
    expect(toCsv([["a;b", "c"]])).toBe('﻿"a;b";c');
  });
});
