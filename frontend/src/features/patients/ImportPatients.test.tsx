import { describe, expect, it } from "vitest";
import { parseCsv } from "@/lib/csv";
import { toImportRows } from "./ImportPatients";

describe("toImportRows", () => {
  it("reconhece o modelo e nomes comuns de colunas, com número da linha", () => {
    const table = parseCsv(
      "Nome;Data Nascimento;CPF;WhatsApp;E-mail;Coluna Estranha\n" +
        "Ana Fictícia;02/03/1985;529.982.247-25;(11) 90000-0000;ana@x.example;abc\n" +
        "Bruno Fictício;;;;;\n",
    );
    const { rows, unknown } = toImportRows(table);
    expect(unknown).toEqual(["Coluna Estranha"]);
    expect(rows).toEqual([
      {
        line: 2,
        fullName: "Ana Fictícia",
        birthDate: "02/03/1985",
        cpf: "529.982.247-25",
        mobile: "(11) 90000-0000",
        email: "ana@x.example",
      },
      { line: 3, fullName: "Bruno Fictício" },
    ]);
  });
});
