"use client";

import { useQuery } from "@tanstack/react-query";
import { Ban } from "lucide-react";
import { useState } from "react";
import styled from "styled-components";
import { ReasonDialog } from "@/components/domain/ReasonDialog";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Card, EmptyState, Muted, Row, Stack } from "@/components/ui/display";
import { FormGrid, FullRow, SelectField, TextAreaField } from "@/components/ui/fields";
import { IconButton } from "@/components/ui/IconButton";
import { LoadingState } from "@/components/ui/LoadingState";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { ClinicalNote, PerformedProcedure } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";

/** Evoluções clínicas: somente inclusão. Correções são feitas com uma nova evolução de retificação. */
export function NotesSection({ patientId }: { patientId: string }) {
  const can = useCan();
  const [amending, setAmending] = useState<ClinicalNote | null>(null);
  const [writing, setWriting] = useState(false);
  const notes = useQuery({ queryKey: ["notes", patientId], queryFn: () => api.clinical.notes(patientId) });

  if (notes.isPending) return <LoadingState />;
  const byId = new Map((notes.data ?? []).map((n) => [n.id, n]));

  return (
    <Stack $gap={4}>
      <Row $justify="space-between">
        <Muted>Evoluções não podem ser editadas nem apagadas. Para corrigir, registre uma retificação.</Muted>
        {can(P.CLINICAL_WRITE) && <Button onClick={() => setWriting(true)}>Nova evolução</Button>}
      </Row>
      {(notes.data?.length ?? 0) === 0 ? (
        <EmptyState title="Nenhuma evolução registrada" />
      ) : (
        <Timeline>
          {notes.data?.map((n) => (
            <li key={n.id}>
              <Card>
                <NoteHead>
                  <strong>{formatDateTime(n.createdAt)}</strong>
                  <Muted>
                    {n.professionalName ?? n.createdByName ?? "—"}
                    {n.amendsNoteId && " · retificação"}
                  </Muted>
                </NoteHead>
                {n.amendsNoteId && byId.get(n.amendsNoteId) && (
                  <Amends>Retifica a evolução de {formatDateTime(byId.get(n.amendsNoteId)?.createdAt)}</Amends>
                )}
                <Content>{n.content}</Content>
                {can(P.CLINICAL_WRITE) && (
                  <Button variant="ghost" onClick={() => setAmending(n)}>
                    Retificar
                  </Button>
                )}
              </Card>
            </li>
          ))}
        </Timeline>
      )}
      {(writing || amending) && (
        <NoteForm
          patientId={patientId}
          amends={amending}
          onClose={() => {
            setWriting(false);
            setAmending(null);
          }}
        />
      )}
    </Stack>
  );
}

function NoteForm({
  patientId,
  amends,
  onClose,
}: {
  patientId: string;
  amends: ClinicalNote | null;
  onClose: () => void;
}) {
  const professionals = useQuery({ queryKey: ["professionals", true], queryFn: () => api.clinic.professionals(true) });
  const [content, setContent] = useState("");
  const [professionalId, setProfessionalId] = useState(amends?.professionalId ?? "");
  const save = useAction(
    () =>
      api.clinical.addNote(patientId, {
        content,
        professionalId: professionalId || null,
        amendsNoteId: amends?.id ?? null,
      }),
    {
      success: "Evolução registrada.",
      invalidate: [
        ["notes", patientId],
        ["record", patientId],
      ],
      onSuccess: onClose,
    },
  );
  return (
    <Modal
      open
      onClose={onClose}
      title={amends ? "Retificar evolução" : "Nova evolução"}
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => save.mutate(undefined)} loading={save.isPending} disabled={!content.trim()}>
            Registrar
          </Button>
        </>
      }
    >
      <Stack $gap={4}>
        {amends && (
          <Alert tone="info" title="Texto original">
            <span style={{ whiteSpace: "pre-wrap" }}>{amends.content}</span>
          </Alert>
        )}
        <SelectField
          label="Profissional"
          placeholder="(selecione)"
          value={professionalId}
          onChange={(e) => setProfessionalId(e.target.value)}
          options={(professionals.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
        />
        <TextAreaField
          label={amends ? "Texto da retificação" : "Evolução"}
          required
          rows={8}
          maxLength={20000}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          hint="Descreva o atendimento: queixa, achados, conduta, materiais, orientações."
        />
        {save.error && <Alert tone="danger">{save.error.message}</Alert>}
      </Stack>
    </Modal>
  );
}

/** Procedimentos realizados (baixam itens de orçamento aprovado quando vinculados). */
export function PerformedSection({ patientId }: { patientId: string }) {
  const can = useCan();
  const [adding, setAdding] = useState(false);
  const [voiding, setVoiding] = useState<PerformedProcedure | null>(null);
  const performed = useQuery({ queryKey: ["performed", patientId], queryFn: () => api.clinical.performed(patientId) });
  const voidIt = useAction((reason: string) => api.clinical.voidPerformed(patientId, voiding?.id ?? "", reason), {
    success: "Registro anulado.",
    invalidate: [["performed", patientId], ["plans"]],
    onSuccess: () => setVoiding(null),
  });

  return (
    <Stack $gap={4}>
      <Row $justify="space-between">
        <Muted>Procedimentos executados no paciente.</Muted>
        {can(P.CLINICAL_WRITE) && <Button onClick={() => setAdding(true)}>Registrar procedimento</Button>}
      </Row>
      <DataTable<PerformedProcedure>
        caption="Procedimentos realizados"
        rows={performed.data}
        loading={performed.isPending}
        rowKey={(p) => p.id}
        emptyTitle="Nenhum procedimento registrado"
        columns={[
          { key: "date", header: "Data", minWidth: "150px", render: (p) => formatDateTime(p.performedAt) },
          {
            key: "desc",
            header: "Procedimento",
            render: (p) => (
              <span style={{ textDecoration: p.voidedAt ? "line-through" : undefined }}>
                {p.description}
                {p.treatmentItemId && <Badge $tone="primary"> do orçamento</Badge>}
              </span>
            ),
          },
          {
            key: "tooth",
            header: "Dente/faces",
            render: (p) => (p.tooth ? `${p.tooth}${p.surfaces ? ` (${p.surfaces})` : ""}` : "—"),
          },
          { key: "prof", header: "Profissional", hideOnMobile: true, render: (p) => p.professionalName },
          {
            key: "status",
            header: "Situação",
            render: (p) =>
              p.voidedAt ? <Badge>Anulado: {p.voidReason}</Badge> : <Badge $tone="success">Realizado</Badge>,
          },
          {
            key: "actions",
            header: "",
            render: (p) =>
              !p.voidedAt &&
              can(P.CLINICAL_WRITE) && (
                <IconButton icon={Ban} label="Anular registro" tone="danger" onClick={() => setVoiding(p)} />
              ),
          },
        ]}
      />
      {adding && <PerformedForm patientId={patientId} onClose={() => setAdding(false)} />}
      <ReasonDialog
        open={Boolean(voiding)}
        title="Anular procedimento"
        description="Se estava vinculado a um orçamento, o item volta a ficar pendente."
        confirmLabel="Anular"
        loading={voidIt.isPending}
        onConfirm={(r) => voidIt.mutate(r)}
        onClose={() => setVoiding(null)}
      />
    </Stack>
  );
}

function PerformedForm({ patientId, onClose }: { patientId: string; onClose: () => void }) {
  const professionals = useQuery({ queryKey: ["professionals", true], queryFn: () => api.clinic.professionals(true) });
  const procedures = useQuery({
    queryKey: ["procedures", "active"],
    queryFn: () => api.clinic.procedures(undefined, true),
  });
  const plans = useQuery({
    queryKey: ["plans", "patient", patientId],
    queryFn: () => api.plans.list({ patientId, status: "APPROVED" }),
  });
  const planDetails = useQuery({
    queryKey: ["plans", "approved-items", patientId, plans.data?.map((p) => p.id).join(",")],
    queryFn: () => Promise.all((plans.data ?? []).map((p) => api.plans.get(p.id))),
    enabled: Boolean(plans.data),
  });
  const pendingItems = (planDetails.data ?? []).flatMap((plan) =>
    plan.items.filter((i) => i.status === "PLANNED").map((i) => ({ ...i, planTitle: plan.title })),
  );

  const [v, setV] = useState({
    professionalId: "",
    procedureId: "",
    description: "",
    tooth: "",
    surfaces: "",
    treatmentItemId: "",
    notes: "",
  });
  const set = (k: keyof typeof v, value: string) => setV((s) => ({ ...s, [k]: value }));
  const save = useAction(
    () =>
      api.clinical.addPerformed(patientId, {
        professionalId: v.professionalId,
        procedureId: v.procedureId || null,
        description: v.description,
        tooth: v.tooth ? Number(v.tooth) : null,
        surfaces: v.surfaces || null,
        treatmentItemId: v.treatmentItemId || null,
        notes: v.notes || undefined,
      }),
    {
      success: "Procedimento registrado.",
      invalidate: [["performed", patientId], ["plans"]],
      onSuccess: onClose,
    },
  );

  const fromItem = (id: string) => {
    const item = pendingItems.find((i) => i.id === id);
    setV((s) => ({
      ...s,
      treatmentItemId: id,
      description: item?.description ?? s.description,
      procedureId: item?.procedureId ?? s.procedureId,
      tooth: item?.tooth ? String(item.tooth) : s.tooth,
      surfaces: item?.surfaces ?? s.surfaces,
    }));
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="Registrar procedimento realizado"
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => save.mutate(undefined)}
            loading={save.isPending}
            disabled={!v.professionalId || !v.description.trim()}
          >
            Registrar
          </Button>
        </>
      }
    >
      <FormGrid $columns={2}>
        {pendingItems.length > 0 && (
          <FullRow>
            <SelectField
              label="Item de orçamento aprovado (opcional)"
              placeholder="Nenhum (procedimento avulso)"
              value={v.treatmentItemId}
              onChange={(e) => fromItem(e.target.value)}
              options={pendingItems.map((i) => ({
                value: i.id,
                label: `${i.planTitle}: ${i.description}${i.tooth ? ` (dente ${i.tooth})` : ""}`,
              }))}
            />
          </FullRow>
        )}
        <SelectField
          label="Profissional"
          required
          placeholder="Selecione…"
          value={v.professionalId}
          onChange={(e) => set("professionalId", e.target.value)}
          options={(professionals.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
        />
        <SelectField
          label="Procedimento do catálogo"
          placeholder="(opcional)"
          value={v.procedureId}
          onChange={(e) => {
            const p = procedures.data?.find((x) => x.id === e.target.value);
            setV((s) => ({ ...s, procedureId: e.target.value, description: s.description || p?.name || "" }));
          }}
          options={(procedures.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
        />
        <FullRow>
          <TextField
            label="Descrição"
            required
            maxLength={200}
            value={v.description}
            onChange={(e) => set("description", e.target.value)}
          />
        </FullRow>
        <TextField
          label="Dente (FDI)"
          type="number"
          min={11}
          max={85}
          value={v.tooth}
          onChange={(e) => set("tooth", e.target.value)}
        />
        <TextField
          label="Faces (ex.: MOD)"
          maxLength={6}
          value={v.surfaces}
          onChange={(e) => set("surfaces", e.target.value.toUpperCase().replace(/[^MODVLPI]/g, ""))}
        />
        <FullRow>
          <TextAreaField
            label="Observações"
            rows={2}
            maxLength={1000}
            value={v.notes}
            onChange={(e) => set("notes", e.target.value)}
          />
        </FullRow>
        {save.error && (
          <FullRow>
            <Alert tone="danger">{save.error.message}</Alert>
          </FullRow>
        )}
      </FormGrid>
    </Modal>
  );
}

const Timeline = styled.ol`
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: ${({ theme }) => theme.space[3]};
`;

const NoteHead = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: ${({ theme }) => theme.space[2]};
  margin-bottom: ${({ theme }) => theme.space[2]};
`;

const Amends = styled.p`
  font-size: ${({ theme }) => theme.fontSizes.sm};
  color: ${({ theme }) => theme.colors.warning};
  margin-bottom: ${({ theme }) => theme.space[2]};
`;

const Content = styled.p`
  white-space: pre-wrap;
  margin-bottom: ${({ theme }) => theme.space[2]};
`;
