"use client";

import { useQuery } from "@tanstack/react-query";
import { Download, FileText, Image as ImageIcon, Pencil, Trash2 } from "lucide-react";
import { useRef, useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog, Modal } from "@/components/ui/Modal";
import { Badge, Card, EmptyState, ErrorState, Muted, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FileField, FormGrid, FullRow, SelectField } from "@/components/ui/fields";
import { IconButton } from "@/components/ui/IconButton";
import { LoadingState } from "@/components/ui/LoadingState";
import { TextField } from "@/components/ui/TextField";
import { apiFetch } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Attachment } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { ATTACHMENT_CATEGORIES } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { useRequiredFields } from "@/lib/useRequiredFields";

const MAX_MB = 15;

export function AttachmentsSection({ patientId }: { patientId: string }) {
  const can = useCan();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [category, setCategory] = useState("XRAY");
  const [description, setDescription] = useState("");
  const [localError, setLocalError] = useState<string>();
  const [removing, setRemoving] = useState<Attachment | null>(null);
  const [editing, setEditing] = useState<Attachment | null>(null);
  const list = useQuery({ queryKey: ["attachments", patientId], queryFn: () => api.clinical.attachments(patientId) });
  const form = useRequiredFields({ file: !file && "Escolha o arquivo a enviar." });

  const upload = useAction(
    () => {
      const form = new FormData();
      form.append("file", file as File);
      form.append("category", category);
      if (description) form.append("description", description);
      return apiFetch<Attachment>(`/api/v1/patients/${patientId}/attachments`, { method: "POST", body: form });
    },
    {
      success: "Arquivo enviado com sucesso!",
      invalidate: [
        ["attachments", patientId],
        ["record", patientId],
      ],
      onSuccess: () => {
        form.reset();
        setFile(null);
        setDescription("");
        if (input.current) input.current.value = "";
      },
    },
  );
  const remove = useAction(() => api.clinical.deleteAttachment(patientId, removing?.id ?? ""), {
    success: "Anexo removido da lista com sucesso!",
    invalidate: [["attachments", patientId]],
    onSuccess: () => setRemoving(null),
  });
  const share = useAction(
    (v: { id: string; shared: boolean }) => api.clinical.setAttachmentSharing(patientId, v.id, v.shared),
    {
      success: (a) =>
        a.sharedWithPatient ? "Anexo liberado no portal do paciente." : "Anexo escondido do portal do paciente.",
      invalidate: [["attachments", patientId]],
    },
  );

  const choose = (f: File | null) => {
    setLocalError(undefined);
    if (f && f.size > MAX_MB * 1024 * 1024) {
      setLocalError(`O arquivo tem ${(f.size / 1024 / 1024).toFixed(1)} MB; o limite é ${MAX_MB} MB.`);
      setFile(null);
      return;
    }
    setFile(f);
  };

  return (
    <Stack $gap={4}>
      {can(P.CLINICAL_WRITE) && (
        <Card>
          <Stack $gap={5}>
            <FormGrid $columns={2}>
              <FileField
                ref={input}
                label="Arquivo"
                hint={`PDF, JPG, PNG ou WEBP, até ${MAX_MB} MB`}
                accept="application/pdf,image/jpeg,image/png,image/webp"
                fileName={file?.name ?? null}
                error={localError ?? form.errors.file}
                onChange={choose}
              />
              <SelectField
                label="Categoria"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                options={ATTACHMENT_CATEGORIES}
              />
              <FullRow>
                <TextField
                  label="Descrição"
                  maxLength={300}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </FullRow>
            </FormGrid>
            {upload.error && <Alert tone="danger">{upload.error.message}</Alert>}
            <Row $justify="flex-end">
              <Button onClick={() => form.check() && upload.mutate(undefined)} loading={upload.isPending}>
                Enviar arquivo
              </Button>
            </Row>
          </Stack>
        </Card>
      )}

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState error={list.error} onRetry={() => list.refetch()} />
      ) : (list.data?.length ?? 0) === 0 ? (
        <EmptyState title="Nenhum anexo" />
      ) : (
        <Grid>
          {list.data?.map((a) => (
            <Item key={a.id}>
              <IconBox aria-hidden>{a.contentType.startsWith("image/") ? <ImageIcon /> : <FileText />}</IconBox>
              <div>
                <strong>{a.description ?? a.filename}</strong>
                <Muted>
                  <Badge $tone="primary">{ATTACHMENT_CATEGORIES.find((c) => c.value === a.category)?.label}</Badge> ·{" "}
                  {(a.sizeBytes / 1024).toFixed(0)} KB · {formatDateTime(a.createdAt)}
                </Muted>
                {can(P.CLINICAL_WRITE) ? (
                  <CheckboxField
                    label="Mostrar ao paciente no portal"
                    checked={a.sharedWithPatient}
                    disabled={share.isPending && share.variables?.id === a.id}
                    onChange={(e) => share.mutate({ id: a.id, shared: e.target.checked })}
                  />
                ) : (
                  a.sharedWithPatient && <Badge $tone="success">Visível no portal do paciente</Badge>
                )}
              </div>
              <Row $gap={2} $wrap={false}>
                <IconLink
                  href={api.clinical.attachmentUrl(patientId, a.id)}
                  target="_blank"
                  rel="noopener"
                  aria-label={`Abrir ${a.filename}`}
                  title="Abrir"
                >
                  <Download size={18} aria-hidden />
                </IconLink>
                {can(P.CLINICAL_WRITE) && (
                  <IconButton icon={Pencil} label={`Editar ${a.filename}`} onClick={() => setEditing(a)} />
                )}
                {can(P.CLINICAL_WRITE) && (
                  <IconButton
                    icon={Trash2}
                    label={`Remover ${a.filename}`}
                    tone="danger"
                    onClick={() => setRemoving(a)}
                  />
                )}
              </Row>
            </Item>
          ))}
        </Grid>
      )}
      {editing && <AttachmentEditForm patientId={patientId} attachment={editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog
        open={Boolean(removing)}
        question="Tem certeza que deseja remover este anexo?"
        message="O anexo sai da lista do paciente, mas o arquivo é mantido para fins de guarda legal do prontuário."
        confirmLabel="Remover"
        tone="danger"
        loading={remove.isPending}
        onConfirm={() => remove.mutate(undefined)}
        onClose={() => setRemoving(null)}
      />
    </Stack>
  );
}

function AttachmentEditForm({
  patientId,
  attachment,
  onClose,
}: {
  patientId: string;
  attachment: Attachment;
  onClose: () => void;
}) {
  const [category, setCategory] = useState<string>(attachment.category);
  const [description, setDescription] = useState(attachment.description ?? "");
  const save = useAction(
    () =>
      api.clinical.updateAttachment(patientId, attachment.id, { category, description: description.trim() || null }),
    {
      success: "Anexo atualizado com sucesso!",
      invalidate: [["attachments", patientId]],
      onSuccess: onClose,
    },
  );
  return (
    <Modal
      open
      onClose={onClose}
      title="Editar anexo"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => save.mutate(undefined)} loading={save.isPending}>
            Salvar
          </Button>
        </>
      }
    >
      <FormGrid $columns={1}>
        <SelectField
          label="Categoria"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          options={ATTACHMENT_CATEGORIES}
        />
        <TextField
          label="Descrição"
          maxLength={300}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <Muted>O arquivo em si não muda. Para trocar o arquivo, remova este anexo e envie o novo.</Muted>
        {save.error && <Alert tone="danger">{save.error.message}</Alert>}
      </FormGrid>
    </Modal>
  );
}

const Grid = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: ${({ theme }) => theme.space[3]};
`;

const Item = styled.li`
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: ${({ theme }) => theme.space[3]};
  padding: ${({ theme }) => theme.space[4]};
  background: ${({ theme }) => theme.colors.surface};
  border-radius: ${({ theme }) => theme.radii.md};
  box-shadow: ${({ theme }) => theme.shadows.card};

  div {
    display: grid;
    gap: 2px;
    min-width: 0;
  }
`;

const IconBox = styled.span`
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: ${({ theme }) => theme.radii.md};
  background: ${({ theme }) => theme.colors.primarySoft};
  color: ${({ theme }) => theme.colors.primaryText};
`;

const IconLink = styled.a`
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: ${({ theme }) => theme.radii.md};
  border: 1px solid ${({ theme }) => theme.colors.border};
  color: ${({ theme }) => theme.colors.textMuted};
  &:hover {
    background: ${({ theme }) => theme.colors.primarySoft};
  }
`;
