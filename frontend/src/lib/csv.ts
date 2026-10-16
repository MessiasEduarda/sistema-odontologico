/**
 * Leitura de planilhas CSV (como o Excel salva no Brasil: separador ";" e acentos em Windows-1252, ou UTF-8 com ",").
 * Sem dependências: aspas, aspas duplicadas e quebras de linha dentro de campos são tratadas.
 */

/** Texto do arquivo: tenta UTF-8; se houver caracteres inválidos, lê como Windows-1252 (padrão do Excel no Brasil). */
export async function readCsvFile(file: Blob): Promise<string> {
  const bytes = await file.arrayBuffer();
  const utf8 = new TextDecoder("utf-8").decode(bytes);
  const text = utf8.includes("�") ? new TextDecoder("windows-1252").decode(bytes) : utf8;
  return text.replace(/^﻿/, "");
}

/** Separador mais provável, olhando a primeira linha. */
function detectDelimiter(text: string): string {
  const firstLine = text.split(/\r?\n/, 1)[0] ?? "";
  const counts = [";", ",", "\t"].map((d) => [d, firstLine.split(d).length - 1] as const);
  counts.sort((a, b) => b[1] - a[1]);
  return counts[0]?.[1] ? counts[0][0] : ";";
}

/** Converte o texto em linhas de células. Linhas totalmente vazias são descartadas. */
export function parseCsv(text: string, delimiter = detectDelimiter(text)): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = "";
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (ch === '"') {
        quoted = false;
      } else {
        cell += ch;
      }
    } else if (ch === '"' && cell === "") {
      quoted = true;
    } else if (ch === delimiter) {
      row.push(cell);
      cell = "";
    } else if (ch === "\n" || ch === "\r") {
      if (ch === "\r" && text[i + 1] === "\n") i++;
      row.push(cell);
      rows.push(row);
      row = [];
      cell = "";
    } else {
      cell += ch;
    }
  }
  row.push(cell);
  rows.push(row);
  return rows.filter((r) => r.some((c) => c.trim() !== ""));
}

/** Normaliza um cabeçalho para comparação: minúsculas, sem acentos e sem pontuação. */
export function normalizeHeader(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

/** Gera CSV (separador ";", com BOM para o Excel abrir os acentos corretamente). */
export function toCsv(rows: string[][]): string {
  const escape = (v: string) => (/[";\n\r]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v);
  return "﻿" + rows.map((r) => r.map(escape).join(";")).join("\r\n");
}
