"use client";

import { useQuery } from "@tanstack/react-query";
import { CalendarPlus, Pencil, Plus, UserMinus } from "lucide-react";
import { useState } from "react";
import styled from "styled-components";
import { PatientSelect } from "@/components/domain/PatientSelect";
import { WhatsAppButton } from "@/components/domain/WhatsAppButton";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Muted, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow, SelectField, TextAreaField } from "@/components/ui/fields";
import { IconButton } from "@/components/ui/IconButton";
import { ConfirmDialog, Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { WaitlistEntry, WaitlistStatus } from "@/lib/api/types";
import { formatDate } from "@/lib/format";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { firstName } from "@/lib/whatsapp";

export const WAITLIST_KEY = ["waitlist"] as const;

const STATUS_OPTIONS: { value: WaitlistStatus; label: string }[] = [
  { value: "WAITING", label: "Aguardando" },
  { value: "SCHEDULED", label: "Encaixados" },
  { value: "REMOVED", label: "Removidos" },
];

/** Quantos pacientes aguardam vaga (para o botão da agenda). */
export function useWaitlistCount() {
  const can = useCan();
  const query = useQuery({
    queryKey: [...WAITLIST_KEY, "WAITING"],
    queryFn: () => api.waitlist.list("WAITING"),
    enabled: can(P.SCHEDULE_READ),
  });
  return query.data?.length ?? 0;
}

/** Mensagem para oferecer um horário que abriu. */
export function offerMessage(entry: WaitlistEntry, clinicName: string | undefined): string {
  const who = entry.professionalName ? ` com ${entry.professionalName}` : "";
  return [
    `Olá, ${firstName(entry.patientName)}! Aqui é da ${clinicName ?? "clínica"}.`,
    `Abriu um horário na agenda${who} e você está na nossa lista de espera.`,
    "Tem interesse? Responda esta mensagem para combinarmos o melhor horário.",
  ].join("\n\n");
}

/**
 * Lista de espera: quem quer adiantar a consulta ou não achou vaga. Quando abre um horário, a recepção chama pelo
 * WhatsApp e agenda dali mesmo (a entrada sai da lista ao salvar o agendamento).
 */
export function WaitlistPanel({ onClose, onSchedule }: { onClose: () => void; onSchedule: (e: WaitlistEntry) => void }) {
  const can = useCan();
  const writable = can(P.SCHEDULE_WRITE);
  const [status, setStatus] = useState<WaitlistStatus>("WAITING");
  const [editing, setEditing] = useState<WaitlistEntry | "new" | null>(null);
  const [removing, setRemoving] = useState<WaitlistEntry | null>(null);
  const list = useQuery({ queryKey: [...WAITLIST_KEY, status], queryFn: () => api.waitlist.list(status) });
  const clinic = useQuery({ queryKey: ["clinic-settings"], queryFn: api.clinic.settings, staleTime: 60_000 });

  const remove = useAction((e: WaitlistEntry) => api.waitlist.resolve(e.id, "REMOVED"), {
    success: "Paciente removido da lista de espera.",
    invalidate: [[...WAITLIST_KEY]],
    onSuccess: () => setRemoving(null),
  });

  const waiting = status === "WAITING";

  return (
    <>
      <Modal open onClose={onClose} title="Lista de espera" size="lg" confirmDiscard={false}>
        <Stack $gap={4}>
          <Muted>
            Pacientes que querem adiantar a consulta ou não acharam vaga. Urgentes aparecem primeiro; depois, por
            ordem de chegada.
          </Muted>
          <Bar>
            <StatusField>
              <SelectField
                label="Mostrar"
                value={status}
                onChange={(e) => setStatus(e.target.value as WaitlistStatus)}
                options={STATUS_OPTIONS}
              />
            </StatusField>
            {writable && (
              <Button onClick={() => setEditing("new")}>
                <Plus size={18} aria-hidden /> Adicionar paciente
              </Button>
            )}
          </Bar>
          <DataTable
            caption="Lista de espera"
            rows={list.data}
            loading={list.isPending}
            error={list.error}
            onRetry={() => void list.refetch()}
            rowKey={(e) => e.id}
            emptyTitle={waiting ? "Ninguém na lista de espera" : "Nada por aqui"}
            emptyDescription={waiting ? "Adicione quem quer ser chamado quando abrir um horário." : undefined}
            columns={[
              {
                key: "patient",
                header: "Paciente",
                render: (e) => (
                  <Who>
                    <strong>{e.patientName}</strong>
                    {e.urgent && <Badge $tone="danger">Urgente</Badge>}
                  </Who>
                ),
              },
              {
                key: "wants",
                header: "Profissional / procedimento",
                hideOnMobile: true,
                render: (e) =>
                  [e.professionalName ?? "Qualquer profissional", e.procedureName].filter(Boolean).join(" · "),
              },
              {
                key: "preference",
                header: "Preferência",
                hideOnMobile: true,
                render: (e) => (
                  <>
                    {e.preference ?? "—"}
                    {e.notes && <Note>{e.notes}</Note>}
                  </>
                ),
              },
              {
                key: "since",
                header: waiting ? "Na lista desde" : "Saiu em",
                render: (e) => formatDate(waiting ? e.createdAt : (e.resolvedAt ?? e.createdAt)),
              },
              ...(waiting && writable
                ? [
                    {
                      key: "actions",
                      header: "",
                      align: "right" as const,
                      render: (e: WaitlistEntry) => (
                        <Actions>
                          <WhatsAppButton
                            phone={e.patientMobile}
                            allowed={e.patientAllowWhatsapp}
                            variant="ghost"
                            label="Chamar"
                            message={() => offerMessage(e, clinic.data?.name)}
                          />
                          <IconButton icon={CalendarPlus} label="Agendar" onClick={() => onSchedule(e)} />
                          <IconButton icon={Pencil} label="Editar" onClick={() => setEditing(e)} />
                          <IconButton
                            icon={UserMinus}
                            label="Remover da lista"
                            tone="danger"
                            onClick={() => setRemoving(e)}
                          />
                        </Actions>
                      ),
                    },
                  ]
                : []),
            ]}
          />
        </Stack>
      </Modal>
      {editing && <WaitlistForm entry={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog
        open={Boolean(removing)}
        question="Remover da lista de espera?"
        message={removing ? `${removing.patientName} deixará de aparecer na lista de espera.` : undefined}
        confirmLabel="Remover"
        tone="danger"
        loading={remove.isPending}
        onConfirm={() => removing && remove.mutate(removing)}
        onClose={() => setRemoving(null)}
      />
    </>
  );
}

/** Adicionar ou editar uma entrada da lista de espera. */
function WaitlistForm({ entry, onClose }: { entry: WaitlistEntry | null; onClose: () => void }) {
  const professionals = useQuery({ queryKey: ["professionals", true], queryFn: () => api.clinic.professionals(true) });
  const procedures = useQuery({
    queryKey: ["procedures", "active"],
    queryFn: () => api.clinic.procedures(undefined, true),
  });
  const [v, setV] = useState({
    patient: entry ? { id: entry.patientId, name: entry.patientName } : null,
    professionalId: entry?.professionalId ?? "",
    procedureId: entry?.procedureId ?? "",
    preference: entry?.preference ?? "",
    notes: entry?.notes ?? "",
    urgent: entry?.urgent ?? false,
  });
  const [error, setError] = useState<string>();
  const set = <K extends keyof typeof v>(k: K, value: (typeof v)[K]) => setV((s) => ({ ...s, [k]: value }));

  const save = useAction(
    () => {
      const body = {
        patientId: v.patient?.id ?? "",
        professionalId: v.professionalId || null,
        procedureId: v.procedureId || null,
        preference: v.preference.trim() || null,
        notes: v.notes.trim() || null,
        urgent: v.urgent,
      };
      return entry ? api.waitlist.update(entry.id, body) : api.waitlist.create(body);
    },
    {
      success: entry ? "Entrada atualizada com sucesso!" : "Paciente adicionado à lista de espera!",
      invalidate: [[...WAITLIST_KEY]],
      onSuccess: onClose,
    },
  );

  const submit = () => {
    if (!v.patient) {
      setError("Selecione o paciente.");
      return;
    }
    setError(undefined);
    save.mutate(undefined);
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={entry ? "Editar lista de espera" : "Adicionar à lista de espera"}
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose} disabled={save.isPending}>
            Cancelar
          </Button>
          <Button onClick={submit} loading={save.isPending}>
            {entry ? "Salvar alterações" : "Adicionar"}
          </Button>
        </>
      }
    >
      <FormGrid $columns={2}>
        <FullRow>
          <PatientSelect
            value={v.patient}
            onChange={(p) => set("patient", p)}
            error={error}
            required
            disabled={Boolean(entry)}
          />
        </FullRow>
        <SelectField
          label="Profissional"
          value={v.professionalId}
          onChange={(e) => set("professionalId", e.target.value)}
          placeholder="Qualquer profissional"
          options={(professionals.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
        />
        <SelectField
          label="Procedimento"
          value={v.procedureId}
          onChange={(e) => set("procedureId", e.target.value)}
          placeholder="Não informado"
          options={(procedures.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
        />
        <FullRow>
          <TextField
            label="Preferência de horário"
            placeholder="Ex.: manhãs, terça ou quinta"
            maxLength={200}
            value={v.preference}
            onChange={(e) => set("preference", e.target.value)}
          />
        </FullRow>
        <FullRow>
          <TextAreaField
            label="Observações"
            rows={2}
            maxLength={500}
            value={v.notes}
            onChange={(e) => set("notes", e.target.value)}
          />
        </FullRow>
        <FullRow>
          <CheckboxField label="Urgente" checked={v.urgent} onChange={(e) => set("urgent", e.target.checked)} />
        </FullRow>
      </FormGrid>
    </Modal>
  );
}

const Bar = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[3]};
`;

const StatusField = styled.div`
  min-width: 200px;
`;

const Who = styled.span`
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[2]};
`;

const Note = styled.span`
  display: block;
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.sm};
`;

const Actions = styled.div`
  display: inline-flex;
  align-items: center;
  gap: ${({ theme }) => theme.space[1]};
`;
