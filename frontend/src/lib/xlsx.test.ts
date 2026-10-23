import { describe, expect, it } from "vitest";
import { buildXlsx, cellFromText } from "./xlsx";
import { readXlsxTable } from "./xlsxRead";

function unzip(bytes: Uint8Array): Record<string, string> {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const decoder = new TextDecoder();
  const files: Record<string, string> = {};
  let at = 0;
  while (view.getUint32(at, true) === 0x04034b50) {
    const size = view.getUint32(at + 18, true);
    const nameLength = view.getUint16(at + 26, true);
    const name = decoder.decode(bytes.subarray(at + 30, at + 30 + nameLength));
    files[name] = decoder.decode(bytes.subarray(at + 30 + nameLength, at + 30 + nameLength + size));
    at += 30 + nameLength + size;
  }
  return files;
}

describe("buildXlsx", () => {
  it("gera uma aba por tabela, com texto, números e caracteres especiais", async () => {
    const blob = buildXlsx([
      {
        name: "Financeiro",
        rows: [
          ["Descrição", "Total"],
          ["Restauração <resina> & polimento", 250.5],
        ],
      },
      { name: "Consultas: 2026/10", rows: [] },
    ]);
    expect(blob.type).toBe("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    const files = unzip(new Uint8Array(await blob.arrayBuffer()));

    expect(Object.keys(files)).toEqual(
      expect.arrayContaining(["[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml"]),
    );
    expect(files["xl/workbook.xml"]).toContain('name="Financeiro"');
    expect(files["xl/workbook.xml"]).toContain('name="Consultas  2026 10"');
    expect(files["xl/worksheets/sheet1.xml"]).toContain("Restauração &lt;resina&gt; &amp; polimento");
    expect(files["xl/worksheets/sheet1.xml"]).toContain("<v>250.5</v>");
    expect(files["xl/worksheets/sheet2.xml"]).toContain("Nenhum registro");
  });
});

describe("cellFromText", () => {
  it("converte números no formato brasileiro e mantém o resto como texto", () => {
    expect(cellFromText("1.234,56")).toBe(1234.56);
    expect(cellFromText("12,5")).toBe(12.5);
    expect(cellFromText("-3")).toBe(-3);
    expect(cellFromText("2026-10")).toBe("2026-10");
    expect(cellFromText("Dra. Lívia")).toBe("Dra. Lívia");
  });
});

describe("readXlsxTable", () => {
  it("lê de volta a planilha gerada (modelo de importação)", async () => {
    const blob = buildXlsx([
      {
        name: "Pacientes",
        rows: [
          ["Nome completo", "CPF", "Observações"],
          ["José Antônio", "01234567890", "Alergia a <látex> & dipirona"],
          [null, null, null],
          ["Ana", "", ""],
        ],
        textColumns: true,
      },
    ]);
    expect(await readXlsxTable(blob)).toEqual([
      ["Nome completo", "CPF", "Observações"],
      ["José Antônio", "01234567890", "Alergia a <látex> & dipirona"],
      ["Ana", "", ""],
    ]);
  });
});
