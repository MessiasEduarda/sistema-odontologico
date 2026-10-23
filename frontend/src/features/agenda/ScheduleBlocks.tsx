"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Muted, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow, SelectField } from "@/components/ui/fields";
import { ConfirmDialog, Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { ScheduleBlock } from "@/lib/api/types";
import { formatDateTime, toDateInput } from "@/lib/format";
import { useAction } from "@/lib/mutations";
import { useRequiredFields } from "@/lib/useRequiredFields";

const REASONS = ["Folga", "Férias", "Feriado", "Almoço", "Curso ou evento", "Manutenção", "Outro"] as const;

export const BLOCKS_KEY = ["schedule-blocks"] as const;

export function useScheduleBlocks(from: Date, to: Date) {
  return useQuery({
    queryKey: [...BLOCKS_KEY, from.toISOString(), to.toISOString()],
    queryFn: () => api.scheduleBlocks.list(from.toISOString(), to.toISOString()),
  });
}

function combine(date: string, time: string): Date {
  const [y, m, d] = date.split("-").map(Number);
  const [h, min] = time.split(":").map(Number);
  return new Date(y ?? 0, (m ?? 1) - 1, d ?? 1, h ?? 0, min ?? 0);
}

export function BlockForm({ initialDate, onClose }: { initialDate: Date; onClose: () => void }) {
  const professionals = useQuery({ queryKey: ["professionals", true], queryFn: () => api.clinic.professionals(true) });
  const [v, setV] = useState({
    professionalId: "",
    fromDate: toDateInput(initialDate),
    toDate: toDateInput(initialDate),
    allDay: true,
    fromTime: "12:00",
    toTime: "13:00",
    reason: "Folga" as string,
    other: "",
  });
  const set = <K extends keyof typeof v>(k: K, value: (typeof v)[K]) => setV((s) => ({ ...s, [k]: value }));

  const startsAt = combine(v.fromDate, v.allDay ? "00:00" : v.fromTime);
  const endsAt = v.allDay
    ? new Date(combine(v.toDate, "00:00").getTime() + 24 * 60 * 60 * 1000)
    : combine(v.toDate, v.toTime);
  const reason = v.reason === "Outro" ? v.other.trim() : v.reason;
  const period = endsAt <= startsAt && "O fim precisa ser depois do início.";
  const form = useRequiredFields({
    fromDate: !v.fromDate && "Informe a data inicial.",
    toDate: (!v.toDate && "Informe a data final.") || (v.allDay && period),
    toTime: !v.allDay && period,
    other: !reason && "Descreva o motivo do bloqueio.",
  });

  const save = useAction(
    () =>
      api.scheduleBlocks.create({
        professionalId: v.professionalId || null,
        startsAt: startsAt.toISOString(),
        endsAt: endsAt.toISOString(),
        reason,
      }),
    {
      success: (b) =>
        b.appointmentsInside > 0
          ? `Bloqueio criado com sucesso! Atenção: há ${b.appointmentsInside} atendimento(s) já marcado(s) nesse período para remarcar.`
          : "Bloqueio criado com sucesso!",
      invalidate: [[...BLOCKS_KEY]],
      onSuccess: onClose,
    },
  );

  return (
    <Modal
      open
      onClose={onClose}
      title="Bloquear horário"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => form.check() && save.mutate(undefined)} loading={save.isPending}>
            Bloquear
          </Button>
        </>
      }
    >
      <Stack $gap={4}>
        <Muted>
          Ninguém consegue marcar atendimento no período bloqueado. Atendimentos já marcados continuam na agenda —
          remarque se precisar.
        </Muted>
        <FormGrid $columns={2}>
          <FullRow>
            <SelectField
              label="Profissional"
              value={v.professionalId}
              onChange={(e) => set("professionalId", e.target.value)}
              placeholder="Clínica inteira (todos)"
              options={(professionals.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
            />
          </FullRow>
          <TextField
            label="De"
            type="date"
            required
            error={form.errors.fromDate}
            value={v.fromDate}
            onChange={(e) => set("fromDate", e.target.value)}
          />
          <TextField
            label="Até"
            type="date"
            required
            error={form.errors.toDate}
            value={v.toDate}
            onChange={(e) => set("toDate", e.target.value)}
          />
          <FullRow>
            <CheckboxField label="Dia inteiro" checked={v.allDay} onChange={(e) => set("allDay", e.target.checked)} />
          </FullRow>
          {!v.allDay && (
            <>
              <TextField label="Das" type="time" value={v.fromTime} onChange={(e) => set("fromTime", e.target.value)} />
              <TextField
                label="Às"
                type="time"
                error={form.errors.toTime}
                value={v.toTime}
                onChange={(e) => set("toTime", e.target.value)}
              />
            </>
          )}
          <SelectField
            label="Motivo"
            value={v.reason}
            onChange={(e) => set("reason", e.target.value)}
            options={REASONS.map((r) => ({ value: r, label: r }))}
          />
          {v.reason === "Outro" && (
            <TextField
              label="Descreva"
              required
              error={form.errors.other}
              maxLength={160}
              value={v.other}
              onChange={(e) => set("other", e.target.value)}
            />
          )}
        </FormGrid>
      </Stack>
    </Modal>
  );
}

export function RemoveBlock({ block, onClose }: { block: ScheduleBlock; onClose: () => void }) {
  const remove = useAction(() => api.scheduleBlocks.remove(block.id), {
    success: "Bloqueio removido com sucesso!",
    invalidate: [[...BLOCKS_KEY]],
    onSuccess: onClose,
  });
  return (
    <ConfirmDialog
      open
      question={`Tem certeza que deseja remover o bloqueio "${block.reason}"?`}
      message={`${block.professionalName ?? "Clínica inteira"} · ${formatDateTime(block.startsAt)} até ${formatDateTime(block.endsAt)}. A agenda volta a aceitar atendimentos nesse período.`}
      confirmLabel="Remover"
      tone="danger"
      loading={remove.isPending}
      onConfirm={() => remove.mutate(undefined)}
      onClose={onClose}
    />
  );
}
