"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import styled from "styled-components";
import { PatientSelect } from "@/components/domain/PatientSelect";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { FormGrid, FullRow, SelectField, TextAreaField } from "@/components/ui/fields";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { Appointment } from "@/lib/api/types";
import { formatTime, localDateTimeToIso, toDateInput, toTimeInput } from "@/lib/format";
import { useAction } from "@/lib/mutations";

export interface AppointmentDraft {
  patient: { id: string; name: string } | null;
  professionalId: string;
  roomId: string;
  procedureId: string;
  date: string;
  time: string;
  duration: number;
  notes: string;
}

export function draftFrom(a: Appointment): AppointmentDraft {
  const start = new Date(a.startsAt);
  return {
    patient: { id: a.patientId, name: a.patientName },
    professionalId: a.professionalId,
    roomId: a.roomId ?? "",
    procedureId: a.procedureId ?? "",
    date: toDateInput(start),
    time: toTimeInput(start),
    duration: Math.round((new Date(a.endsAt).getTime() - start.getTime()) / 60000),
    notes: a.notes ?? "",
  };
}

interface Props {
  open: boolean;
  initial: AppointmentDraft;
  editing?: Appointment | null;
  onClose: () => void;
}

/** Agendar ou remarcar. Mostra horários livres do profissional no dia escolhido. */
export function AppointmentForm({ open, initial, editing, onClose }: Props) {
  const [draft, setDraft] = useState(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const set = <K extends keyof AppointmentDraft>(key: K, value: AppointmentDraft[K]) =>
    setDraft((d) => ({ ...d, [key]: value }));

  const professionals = useQuery({ queryKey: ["professionals", true], queryFn: () => api.clinic.professionals(true) });
  const procedures = useQuery({
    queryKey: ["procedures", "active"],
    queryFn: () => api.clinic.procedures(undefined, true),
  });
  const rooms = useQuery({ queryKey: ["rooms"], queryFn: api.clinic.rooms });
  const availability = useQuery({
    queryKey: ["availability", draft.professionalId, draft.date, draft.duration],
    queryFn: () => api.appointments.availability(draft.professionalId, draft.date, draft.duration),
    enabled: Boolean(draft.professionalId && draft.date && draft.duration >= 5),
  });

  const save = useAction(
    () => {
      const startsAt = localDateTimeToIso(draft.date, draft.time);
      const endsAt = new Date(new Date(startsAt).getTime() + draft.duration * 60000).toISOString();
      const body = {
        patientId: draft.patient?.id ?? "",
        professionalId: draft.professionalId,
        roomId: draft.roomId || null,
        procedureId: draft.procedureId || null,
        startsAt,
        endsAt,
        notes: draft.notes || null,
        version: editing?.version,
      };
      return editing ? api.appointments.update(editing.id, body) : api.appointments.create(body);
    },
    {
      success: editing ? "Agendamento atualizado com sucesso!" : "Atendimento agendado com sucesso!",
      invalidate: [["appointments"], ["dashboard"], ["availability"]],
      onSuccess: onClose,
    },
  );

  const submit = () => {
    const e: Record<string, string> = {};
    if (!draft.patient) e.patient = "Selecione o paciente.";
    if (!draft.professionalId) e.professional = "Selecione o profissional.";
    if (!draft.date) e.date = "Informe a data.";
    if (!draft.time) e.time = "Informe o horário.";
    if (!draft.duration || draft.duration < 5) e.duration = "Duração mínima de 5 minutos.";
    setErrors(e);
    if (Object.keys(e).length === 0) save.mutate(undefined);
  };

  const onProcedure = (id: string) => {
    const proc = procedures.data?.find((p) => p.id === id);
    setDraft((d) => ({ ...d, procedureId: id, duration: proc ? proc.durationMinutes : d.duration }));
  };

  const activeRooms = (rooms.data ?? []).filter((r) => r.active);

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={editing ? "Editar / remarcar atendimento" : "Novo agendamento"}
      size="lg"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose} disabled={save.isPending}>
            Cancelar
          </Button>
          <Button onClick={submit} loading={save.isPending}>
            {editing ? "Salvar alterações" : "Agendar"}
          </Button>
        </>
      }
    >
      <FormGrid $columns={2}>
        <FullRow>
          <PatientSelect
            value={draft.patient}
            onChange={(p) => set("patient", p)}
            error={errors.patient}
            required
            disabled={Boolean(editing)}
          />
        </FullRow>
        <SelectField
          label="Profissional"
          required
          placeholder="Selecione…"
          value={draft.professionalId}
          error={errors.professional}
          onChange={(e) => set("professionalId", e.target.value)}
          options={(professionals.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
        />
        <SelectField
          label="Procedimento"
          placeholder="(opcional)"
          value={draft.procedureId}
          onChange={(e) => onProcedure(e.target.value)}
          options={(procedures.data ?? []).map((p) => ({ value: p.id, label: `${p.name} (${p.durationMinutes} min)` }))}
        />
        <TextField
          label="Data"
          type="date"
          required
          value={draft.date}
          error={errors.date}
          onChange={(e) => set("date", e.target.value)}
        />
        <TimeRow>
          <TextField
            label="Início"
            type="time"
            step={300}
            required
            value={draft.time}
            error={errors.time}
            onChange={(e) => set("time", e.target.value)}
          />
          <TextField
            label="Duração (min)"
            type="number"
            min={5}
            max={480}
            step={5}
            required
            value={draft.duration}
            error={errors.duration}
            onChange={(e) => set("duration", Number(e.target.value))}
          />
        </TimeRow>
        {draft.professionalId && draft.date && (
          <FullRow>
            <SlotsLabel>Horários livres na grade do profissional</SlotsLabel>
            {availability.isPending ? (
              <Muted>Carregando…</Muted>
            ) : (availability.data?.length ?? 0) === 0 ? (
              <Muted>
                Nenhum horário livre na grade de atendimento deste dia (você ainda pode agendar manualmente).
              </Muted>
            ) : (
              <Slots>
                {availability.data?.slice(0, 24).map((s) => {
                  const t = toTimeInput(new Date(s.startsAt));
                  return (
                    <SlotButton
                      key={s.startsAt}
                      type="button"
                      aria-pressed={draft.time === t}
                      onClick={() => set("time", t)}
                    >
                      {formatTime(s.startsAt)}
                    </SlotButton>
                  );
                })}
              </Slots>
            )}
          </FullRow>
        )}
        <SelectField
          label="Sala"
          placeholder="(opcional)"
          value={draft.roomId}
          onChange={(e) => set("roomId", e.target.value)}
          options={activeRooms.map((r) => ({ value: r.id, label: r.name }))}
        />
        <FullRow>
          <TextAreaField
            label="Observações"
            rows={2}
            maxLength={2000}
            value={draft.notes}
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

const TimeRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: ${({ theme }) => theme.space[3]};
`;

const SlotsLabel = styled.p`
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  margin-bottom: ${({ theme }) => theme.space[2]};
`;

const Muted = styled.p`
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.sm};
`;

const Slots = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[2]};
`;

const SlotButton = styled.button`
  border: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.surface};
  border-radius: ${({ theme }) => theme.radii.pill};
  padding: 4px 12px;
  cursor: pointer;
  font-family: ${({ theme }) => theme.fonts.mono};
  font-size: ${({ theme }) => theme.fontSizes.sm};

  &:hover,
  &[aria-pressed="true"] {
    border-color: ${({ theme }) => theme.colors.primary};
    background: ${({ theme }) => theme.colors.primarySoft};
  }
`;
