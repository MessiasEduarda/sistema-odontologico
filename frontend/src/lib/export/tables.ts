import type { XlsxCell, XlsxSheet } from "@/lib/xlsx";

export interface ExportColumn {
  header: string;
  width: number;
  money?: boolean;
  align?: "left" | "right" | "center";
}

export interface ExportTable {
  title: string;
  columns: ExportColumn[];
  rows: XlsxCell[][];
  note?: string;
}

export function tablesToSheets(tables: ExportTable[]): XlsxSheet[] {
  return tables.map((t) => ({
    name: t.title,
    rows: t.note ? [[t.note]] : [t.columns.map((c) => c.header), ...t.rows],
  }));
}
