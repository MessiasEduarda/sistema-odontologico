"use client";

import { Download, FileSpreadsheet, FileText, type LucideIcon } from "lucide-react";
import { useState } from "react";
import styled from "styled-components";
import { Button } from "@/components/ui/Button";
import { useFeedback } from "@/components/ui/Feedback";
import { Modal } from "@/components/ui/Modal";
import type { ExportFormat } from "@/lib/export/useExport";

interface ExportButtonProps {
  label?: string;
  title: string;
  description?: string;
  onExport: (format: ExportFormat) => Promise<void>;
  variant?: "outline" | "ghost";
}

const OPTIONS: { format: ExportFormat; icon: LucideIcon; title: string; hint: string }[] = [
  { format: "pdf", icon: FileText, title: "PDF", hint: "Documento para ler, imprimir ou entregar." },
  { format: "excel", icon: FileSpreadsheet, title: "Excel", hint: "Planilha para conferir, filtrar e somar." },
];

export function ExportButton({
  label = "Exportar",
  title,
  description,
  onExport,
  variant = "outline",
}: ExportButtonProps) {
  const feedback = useFeedback();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState<ExportFormat | null>(null);

  const run = async (format: ExportFormat) => {
    setBusy(format);
    try {
      await onExport(format);
      feedback.success(`Arquivo ${format === "pdf" ? "PDF" : "do Excel"} gerado com sucesso!`);
      setOpen(false);
    } catch (error) {
      feedback.error("Não foi possível exportar", error instanceof Error ? error.message : undefined);
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <Button variant={variant} onClick={() => setOpen(true)}>
        <Download size={16} aria-hidden /> {label}
      </Button>
      <Modal open={open} title={title} onClose={() => setOpen(false)} size="sm" confirmDiscard={false}>
        {description && <Description>{description}</Description>}
        <Choices role="group" aria-label="Formato do arquivo">
          {OPTIONS.map(({ format, icon: Icon, title: name, hint }) => (
            <Choice
              key={format}
              type="button"
              onClick={() => void run(format)}
              disabled={busy !== null}
              aria-busy={busy === format || undefined}
            >
              <ChoiceIcon aria-hidden="true">
                <Icon size={22} strokeWidth={1.6} />
              </ChoiceIcon>
              <span>
                <strong>{busy === format ? "Gerando…" : name}</strong>
                <small>{hint}</small>
              </span>
            </Choice>
          ))}
        </Choices>
      </Modal>
    </>
  );
}

const Description = styled.p`
  color: ${({ theme }) => theme.colors.textMuted};
  margin-bottom: ${({ theme }) => theme.space[4]};
`;

const Choices = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[3]};
`;

const Choice = styled.button`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space[4]};
  width: 100%;
  padding: ${({ theme }) => theme.space[4]};
  text-align: left;
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radii.md};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.text};
  cursor: pointer;

  strong {
    display: block;
    font-weight: ${({ theme }) => theme.fontWeights.semibold};
  }
  small {
    display: block;
    color: ${({ theme }) => theme.colors.textMuted};
    font-size: ${({ theme }) => theme.fontSizes.sm};
  }

  &:hover:not(:disabled) {
    border-color: ${({ theme }) => theme.colors.primary};
    background: ${({ theme }) => theme.colors.primarySoft};
  }
  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
  &:disabled {
    cursor: progress;
    opacity: 0.7;
  }
`;

const ChoiceIcon = styled.span`
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: ${({ theme }) => theme.radii.md};
  background: ${({ theme }) => theme.colors.primarySoft};
  color: ${({ theme }) => theme.colors.primaryText};
`;
