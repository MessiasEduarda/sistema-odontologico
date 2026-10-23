import { api } from "@/lib/api/endpoints";
import { parseCsv } from "@/lib/csv";
import { cellFromText } from "@/lib/xlsx";
import type { ExportTable } from "./tables";

export type ReportType = "agenda" | "production" | "financial";

export const REPORT_TITLES: Record<ReportType, string> = {
  agenda: "Ocupação por profissional",
  production: "Produção por profissional",
  financial: "Movimento por mês",
};

export async function loadReportTable(type: ReportType, from: string, to: string): Promise<ExportTable> {
  const response = await fetch(api.reports.exportUrl(type, from, to), { credentials: "same-origin" });
  if (!response.ok) throw new Error("O servidor não devolveu o relatório. Tente novamente.");
  const [header = [], ...rows] = parseCsv((await response.text()).replace(/^\uFEFF/, ""));
  const width = Math.floor(100 / Math.max(1, header.length));
  return {
    title: REPORT_TITLES[type],
    columns: header.map((h, i) => ({
      header: h,
      width,
      money: h.includes("(R$)"),
      align: i === 0 ? "left" : "right",
    })),
    rows: rows.map((r) => r.map((cell, i) => (i === 0 ? cell : cellFromText(cell)))),
  };
}
