"use client";

import { FileDown } from "lucide-react";
import { useState, type MouseEvent } from "react";
import { Button } from "@/components/ui/Button";
import { IconButton } from "@/components/ui/IconButton";
import { useToast } from "@/components/ui/Toast";
import { type PdfRequest, usePdf } from "@/lib/pdf/usePdf";

/**
 * Botão "Baixar PDF": um clique gera e baixa o arquivo do paciente. `load` pode buscar os dados completos (ex.: a
 * partir de uma linha de tabela que só tem o resumo). `compact` vira ícone para colunas de ação.
 */
export function PdfButton({
  load,
  label = "Baixar PDF",
  compact = false,
  variant = "outline",
}: {
  load: () => PdfRequest | Promise<PdfRequest>;
  label?: string;
  compact?: boolean;
  variant?: "outline" | "ghost" | "primary";
}) {
  const { download } = usePdf();
  const toast = useToast();
  const [pending, setPending] = useState(false);

  const onClick = async (e: MouseEvent) => {
    e.stopPropagation();
    if (pending) return;
    setPending(true);
    try {
      await download(await load());
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Não foi possível gerar o PDF.");
    } finally {
      setPending(false);
    }
  };

  if (compact) {
    return <IconButton icon={FileDown} label={pending ? "Gerando PDF…" : label} disabled={pending} onClick={onClick} />;
  }
  return (
    <Button variant={variant} onClick={onClick} loading={pending}>
      <FileDown size={16} aria-hidden /> {label}
    </Button>
  );
}
