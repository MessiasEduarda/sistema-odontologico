"use client";

import { FileDown } from "lucide-react";
import { useState, type MouseEvent } from "react";
import { Button } from "@/components/ui/Button";
import { IconButton } from "@/components/ui/IconButton";
import { useFeedback } from "@/components/ui/Feedback";
import { type PdfRequest, usePdf } from "@/lib/pdf/usePdf";

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
  const feedback = useFeedback();
  const [pending, setPending] = useState(false);

  const onClick = async (e: MouseEvent) => {
    e.stopPropagation();
    if (pending) return;
    setPending(true);
    try {
      await download(await load());
    } catch (error) {
      feedback.error("Não foi possível gerar o PDF", error instanceof Error ? error.message : undefined);
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
