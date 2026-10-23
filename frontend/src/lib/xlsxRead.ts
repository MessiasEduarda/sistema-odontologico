interface ZipEntry {
  method: number;
  compressedSize: number;
  dataStart: number;
}

function zipEntries(bytes: Uint8Array): Map<string, ZipEntry> {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let end = -1;
  for (let i = bytes.length - 22; i >= Math.max(0, bytes.length - 65_557); i--) {
    if (view.getUint32(i, true) === 0x06054b50) {
      end = i;
      break;
    }
  }
  if (end < 0) throw new Error("O arquivo não é uma planilha do Excel válida.");
  const count = view.getUint16(end + 10, true);
  let at = view.getUint32(end + 16, true);
  const decoder = new TextDecoder();
  const entries = new Map<string, ZipEntry>();
  for (let n = 0; n < count && view.getUint32(at, true) === 0x02014b50; n++) {
    const method = view.getUint16(at + 10, true);
    const compressedSize = view.getUint32(at + 20, true);
    const nameLength = view.getUint16(at + 28, true);
    const extraLength = view.getUint16(at + 30, true);
    const commentLength = view.getUint16(at + 32, true);
    const local = view.getUint32(at + 42, true);
    const name = decoder.decode(bytes.subarray(at + 46, at + 46 + nameLength));
    const dataStart = local + 30 + view.getUint16(local + 26, true) + view.getUint16(local + 28, true);
    entries.set(name, { method, compressedSize, dataStart });
    at += 46 + nameLength + extraLength + commentLength;
  }
  return entries;
}

async function readEntry(bytes: Uint8Array, entry: ZipEntry | undefined): Promise<string | null> {
  if (!entry) return null;
  const data = bytes.slice(entry.dataStart, entry.dataStart + entry.compressedSize);
  if (entry.method === 0) return new TextDecoder().decode(data);
  if (entry.method !== 8) throw new Error("Formato de compactação não suportado nesta planilha.");
  const stream = new ReadableStream<BufferSource>({
    start(controller) {
      controller.enqueue(data);
      controller.close();
    },
  }).pipeThrough(new DecompressionStream("deflate-raw"));
  const chunks: Uint8Array[] = [];
  const reader = stream.getReader();
  for (let r = await reader.read(); !r.done; r = await reader.read()) chunks.push(r.value);
  const out = new Uint8Array(chunks.reduce((n, c) => n + c.length, 0));
  let at = 0;
  for (const c of chunks) {
    out.set(c, at);
    at += c.length;
  }
  return new TextDecoder().decode(out);
}

const textOf = (node: Element) =>
  Array.from(node.getElementsByTagName("t"))
    .map((t) => t.textContent ?? "")
    .join("");

function columnIndex(ref: string): number {
  let index = 0;
  for (const ch of ref.replace(/\d+$/, "")) index = index * 26 + (ch.charCodeAt(0) - 64);
  return index - 1;
}

const BUILT_IN_DATE_FORMATS = new Set([14, 15, 16, 17, 18, 19, 20, 21, 22, 45, 46, 47]);

function dateStyles(stylesXml: string | null): Set<number> {
  const result = new Set<number>();
  if (!stylesXml) return result;
  const doc = new DOMParser().parseFromString(stylesXml, "application/xml");
  const custom = new Map<number, string>();
  for (const f of Array.from(doc.getElementsByTagName("numFmt"))) {
    custom.set(Number(f.getAttribute("numFmtId")), f.getAttribute("formatCode") ?? "");
  }
  const cellXfs = doc.getElementsByTagName("cellXfs")[0];
  Array.from(cellXfs?.getElementsByTagName("xf") ?? []).forEach((xf, i) => {
    const id = Number(xf.getAttribute("numFmtId") ?? 0);
    const code = custom.get(id)?.replace(/"[^"]*"|\[[^\]]*\]/g, "") ?? "";
    if (BUILT_IN_DATE_FORMATS.has(id) || /[dy]/i.test(code)) result.add(i);
  });
  return result;
}

function serialToDate(serial: number): string {
  const date = new Date(Date.UTC(1899, 11, 30) + Math.round(serial * 86_400_000));
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(date.getUTCDate())}/${pad(date.getUTCMonth() + 1)}/${date.getUTCFullYear()}`;
}

export async function readXlsxTable(file: Blob): Promise<string[][]> {
  const bytes = new Uint8Array(await file.arrayBuffer());
  const entries = zipEntries(bytes);
  const sheetName =
    [...entries.keys()]
      .filter((n) => /^xl\/worksheets\/sheet\d+\.xml$/.test(n))
      .sort((a, b) => {
        const num = (s: string) => Number(s.match(/(\d+)\.xml$/)?.[1]);
        return num(a) - num(b);
      })[0] ?? "";
  const [sheetXml, sharedXml, stylesXml] = await Promise.all([
    readEntry(bytes, entries.get(sheetName)),
    readEntry(bytes, entries.get("xl/sharedStrings.xml")),
    readEntry(bytes, entries.get("xl/styles.xml")),
  ]);
  if (!sheetXml) throw new Error("Não encontrei nenhuma aba nesta planilha.");

  const parser = new DOMParser();
  const shared = sharedXml
    ? Array.from(parser.parseFromString(sharedXml, "application/xml").getElementsByTagName("si")).map(textOf)
    : [];
  const dates = dateStyles(stylesXml);
  const sheet = parser.parseFromString(sheetXml, "application/xml");

  const rows: string[][] = [];
  for (const row of Array.from(sheet.getElementsByTagName("row"))) {
    const cells: string[] = [];
    Array.from(row.getElementsByTagName("c")).forEach((c, position) => {
      const ref = c.getAttribute("r");
      const index = ref ? columnIndex(ref) : position;
      const type = c.getAttribute("t");
      const raw = c.getElementsByTagName("v")[0]?.textContent ?? "";
      let value: string;
      if (type === "s") value = shared[Number(raw)] ?? "";
      else if (type === "inlineStr") value = textOf(c);
      else if (type === "b") value = raw === "1" ? "sim" : "não";
      else if (type === "str" || type === "e") value = raw;
      else if (raw !== "" && dates.has(Number(c.getAttribute("s") ?? 0))) value = serialToDate(Number(raw));
      else value = raw;
      cells[index] = value;
    });
    const filled = Array.from(cells, (v) => v ?? "");
    if (filled.some((v) => v.trim() !== "")) rows.push(filled);
  }
  return rows;
}
