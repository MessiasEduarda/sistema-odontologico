"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { FileField, FormGrid, FullRow, SelectField, TextAreaField } from "@/components/ui/fields";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { TicketCategory, TicketDetail, TicketImpact } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { TICKET_CATEGORIES, TICKET_IMPACTS } from "@/lib/labels";
import { useAction } from "@/lib/mutations";
import type { ProblemReport } from "@/lib/support-report";
import { useRequiredFields } from "@/lib/useRequiredFields";

const MAX_FILE_BYTES = 5 * 1024 * 1024;

export function NewTicketForm({
  report,
  related,
  onClose,
}: {
  report?: ProblemReport | null;
  related?: { id: string; protocol: string } | null;
  onClose: () => void;
}) {
  const router = useRouter();
  const [category, setCategory] = useState<string>(report ? "BUG" : "");
  const [impact, setImpact] = useState<string>("");
  const [subject, setSubject] = useState(related ? `Continuação do chamado ${related.protocol}` : "");
  const [description, setDescription] = useState(
    report ? `O que eu estava fazendo:\n\nO que apareceu na tela: ${report.message}` : "",
  );
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string>();

  const form = useRequiredFields({
    category: !category && "Escolha a categoria.",
    impact: !impact && "Diga o quanto isso atrapalha o trabalho.",
    subject: !subject.trim() && "Informe o assunto.",
    description: !description.trim() && "Descreva o que aconteceu.",
  });

  const open = useAction(
    async () => {
      const ticket = await api.support.open({
        subject: subject.trim(),
        category: category as TicketCategory,
        impact: impact as TicketImpact,
        description: description.trim(),
        page: report?.page ?? null,
        traceId: report?.traceId ?? null,
        errorMessage: report?.message ?? null,
        relatedTicketId: related?.id ?? null,
      });
      return file ? api.support.attach(ticket.id, file) : ticket;
    },
    {
      success: (t: TicketDetail) =>
        `Chamado ${t.protocol} aberto! A primeira resposta chega até ${formatDateTime(t.firstResponseDueAt)}.`,
      invalidate: [["support-tickets"], ["support-unread"]],
      onSuccess: (t) => {
        onClose();
        router.push(`/suporte/${t.id}`);
      },
    },
  );

  const choose = (next: File | null) => {
    setFileError(undefined);
    if (next && next.size > MAX_FILE_BYTES) {
      setFile(null);
      setFileError("O arquivo pode ter no máximo 5 MB.");
      return;
    }
    setFile(next);
  };

  const chosenImpact = TICKET_IMPACTS.find((i) => i.value === impact);

  return (
    <Modal
      open
      onClose={onClose}
      title="Abrir chamado"
      size="lg"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => form.check() && !fileError && open.mutate(undefined)} loading={open.isPending}>
            Enviar chamado
          </Button>
        </>
      }
    >
      <FormGrid $columns={2}>
        <FullRow>
          <Alert tone="info">
            Não informe dados de pacientes (nome, CPF, prontuário). Descreva a situação e, se precisar, cite só o número
            do atendimento.
          </Alert>
        </FullRow>
        {report && (
          <FullRow>
            <Alert tone="warning" title="Informações do erro enviadas junto">
              Tela: {report.page}
              {report.traceId ? ` · Código do erro: ${report.traceId}` : ""}
            </Alert>
          </FullRow>
        )}
        <SelectField
          label="Categoria"
          required
          error={form.errors.category}
          placeholder="Selecione…"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          options={TICKET_CATEGORIES}
        />
        <SelectField
          label="Quanto isso atrapalha?"
          required
          error={form.errors.impact}
          hint={chosenImpact?.hint}
          placeholder="Selecione…"
          value={impact}
          onChange={(e) => setImpact(e.target.value)}
          options={TICKET_IMPACTS}
        />
        <FullRow>
          <TextField
            label="Assunto"
            required
            maxLength={160}
            error={form.errors.subject}
            placeholder="Ex.: A agenda não abre"
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
          />
        </FullRow>
        <FullRow>
          <TextAreaField
            label="Descreva o que aconteceu"
            required
            rows={7}
            maxLength={5000}
            error={form.errors.description}
            hint="Conte o passo a passo até o problema e o que você esperava que acontecesse."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </FullRow>
        <FullRow>
          <FileField
            label="Print da tela (opcional)"
            accept="image/png,image/jpeg,image/webp,application/pdf"
            hint="PNG, JPG, WEBP ou PDF de até 5 MB. Dá para anexar mais arquivos depois, na conversa."
            fileName={file?.name ?? null}
            error={fileError}
            onChange={choose}
          />
        </FullRow>
      </FormGrid>
    </Modal>
  );
}
