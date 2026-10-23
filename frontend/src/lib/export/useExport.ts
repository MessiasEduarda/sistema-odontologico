"use client";

import { useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/endpoints";
import type { Patient } from "@/lib/api/types";
import { downloadBlob } from "@/lib/download";
import { buildXlsx } from "@/lib/xlsx";
import { tablesToSheets, type ExportTable } from "./tables";

export type ExportFormat = "pdf" | "excel";

export interface ExportContent {
  title: string;
  subtitle?: string;
  tables: ExportTable[];
  footnote?: string;
  patient?: Patient;
  filename: string;
}

export function useExport() {
  const queryClient = useQueryClient();

  return async function exportAs(format: ExportFormat, content: ExportContent) {
    if (format === "excel") {
      downloadBlob(buildXlsx(tablesToSheets(content.tables)), `${content.filename}.xlsx`);
      return;
    }
    const [{ pdf }, docs, clinic] = await Promise.all([
      import("@react-pdf/renderer"),
      import("@/lib/pdf/documents"),
      queryClient.fetchQuery({ queryKey: ["clinic-settings"], queryFn: api.clinic.settings, staleTime: 60_000 }),
    ]);
    const element = docs.TablesPdf({
      ctx: { clinic, patient: content.patient, logo: null },
      title: content.title,
      subtitle: content.subtitle,
      tables: content.tables,
      footnote: content.footnote,
    });
    downloadBlob(await pdf(element).toBlob(), `${content.filename}.pdf`);
  };
}
