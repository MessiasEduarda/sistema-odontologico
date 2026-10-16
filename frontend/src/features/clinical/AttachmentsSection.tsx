"use client";

import { useQuery } from "@tanstack/react-query";
import { Download, FileText, Image as ImageIcon, Trash2 } from "lucide-react";
import { useRef, useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/Modal";
import { Badge, Card, EmptyState, Muted, Row, Stack } from "@/components/ui/display";
import { FormGrid, SelectField } from "@/components/ui/fields";
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

const MAX_MB = 15;

/** Radiografias, fotos, exames e documentos digitalizados. Cada download é registrado na auditoria. */
export function AttachmentsSection({ patientId }: { patientId: string }) {
  const can = useCan();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [category, setCategory] = useState("XRAY");
  const [description, setDescription] = useState("");
  const [localError, setLocalError] = useState<string>();
  const [removing, setRemoving] = useState<Attachment | null>(null);
  const list = useQuery({ queryKey: ["attachments", patientId], queryFn: () => api.clinical.attachments(patientId) });

  const upload = useAction(
    () => {
      const form = new FormData();
      form.append("file", file as File);
      form.append("category", category);
      if (description) form.append("description", description);
      return apiFetch<Attachment>(`/api/v1/patients/${patientId}/attachments`, { method: "POST", body: form });
    },
    {
      success: "Arquivo enviado.",
      invalidate: [
        ["attachments", patientId],
        ["record", patientId],
      ],
      onSuccess: () => {
        setFile(null);
        setDescription("");
        if (input.current) input.current.value = "";
      },
    },
  );
  const remove = useAction(() => api.clinical.deleteAttachment(patientId, removing?.id ?? ""), {
    success: "Anexo removido da lista.",
    invalidate: [["attachments", patientId]],
    onSuccess: () => setRemoving(null),
  });

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
          <FormGrid $columns={3}>
            <div>
              <Label htmlFor="attachment-file">Arquivo (PDF, JPG, PNG, WEBP · até {MAX_MB} MB)</Label>
              <input
                id="attachment-file"
                ref={input}
                type="file"
                accept="application/pdf,image/jpeg,image/png,image/webp"
                onChange={(e) => choose(e.target.files?.[0] ?? null)}
              />
            </div>
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
          </FormGrid>
          {localError && <Alert tone="danger">{localError}</Alert>}
          {upload.error && <Alert tone="danger">{upload.error.message}</Alert>}
          <Row $justify="flex-end">
            <Button onClick={() => upload.mutate(undefined)} loading={upload.isPending} disabled={!file}>
              Enviar arquivo
            </Button>
          </Row>
        </Card>
      )}

      {list.isPending ? (
        <LoadingState />
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
      <ConfirmDialog
        open={Boolean(removing)}
        title="Remover anexo"
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

const Label = styled.label`
  display: block;
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  margin-bottom: ${({ theme }) => theme.space[2]};
`;

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
