"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import styled from "styled-components";
import { ReasonDialog } from "@/components/domain/ReasonDialog";
import { WhatsAppButton } from "@/components/domain/WhatsAppButton";
import { Button } from "@/components/ui/Button";
import { Badge, DetailList, ErrorState, Muted, Stack } from "@/components/ui/display";
import { LoadingState } from "@/components/ui/LoadingState";
import { Modal } from "@/components/ui/Modal";
import { api } from "@/lib/api/endpoints";
import type { Appointment } from "@/lib/api/types";
import { formatDate, formatDateTime, formatPhone, formatTime } from "@/lib/format";
import { APPOINTMENT_ACTIONS, APPOINTMENT_STATUS } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { firstName } from "@/lib/whatsapp";
import { useWaitlistCount } from "./Waitlist";
import { P } from "@/lib/permissions";

const HISTORY_LABELS: Record<string, string> = {
  CREATED: "Agendado",
  UPDATED: "Dados alterados",
  RESCHEDULED: "Remarcado",
  STATUS_CHANGED: "Status alterado",
};

interface Props {
  appointmentId: string | null;
  onClose: () => void;
  onEdit: (a: Appointment) => void;
}

export function AppointmentDetail({ appointmentId, onClose, onEdit }: Props) {
  const can = useCan();
  const [cancelling, setCancelling] = useState(false);
  const waitingCount = useWaitlistCount();
  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ["appointments", "detail", appointmentId],
    queryFn: () => api.appointments.detail(appointmentId ?? ""),
    enabled: Boolean(appointmentId),
  });

  const changeStatus = useAction(
    ({ status, reason }: { status: string; reason?: string }) =>
      api.appointments.status(appointmentId ?? "", status, reason),
    {
      success: (r) =>
        r.status === "CANCELLED" && waitingCount > 0
          ? `Atendimento cancelado. Há ${waitingCount} paciente(s) na lista de espera — veja quem pode ocupar o horário.`
          : "Status atualizado com sucesso!",
      invalidate: [["appointments"], ["dashboard"]],
      onSuccess: () => setCancelling(false),
    },
  );

  const a = data?.appointment;
  const writable = can(P.SCHEDULE_WRITE);
  const patient = useQuery({
    queryKey: ["patient", a?.patientId],
    queryFn: () => api.patients.get(a?.patientId ?? ""),
    enabled: Boolean(a?.patientId) && can(P.PATIENTS_READ),
    staleTime: 60_000,
  });
  const clinic = useQuery({ queryKey: ["clinic-settings"], queryFn: api.clinic.settings, staleTime: 60_000 });

  const reminder = async () => {
    if (!a) return "";
    const { path } = await api.appointments.patientLink(a.id);
    return [
      `Olá, ${firstName(a.patientName)}! Aqui é da ${clinic.data?.name ?? "clínica"}.`,
      `Lembrete da sua consulta em ${formatDate(a.startsAt)} às ${formatTime(a.startsAt)} com ${a.professionalName}.`,
      `Para confirmar ou cancelar: ${window.location.origin}${path}`,
      "Se precisar remarcar, é só responder esta mensagem.",
    ].join("\n\n");
  };

  return (
    <>
      <Modal open={Boolean(appointmentId)} onClose={onClose} title="Atendimento" size="md">
        {isError ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : isPending || !a ? (
          <LoadingState />
        ) : (
          <Stack $gap={5}>
            <Head>
              <div>
                <PatientName href={`/pacientes/${a.patientId}`}>{a.patientName}</PatientName>
                <Muted>
                  {formatDate(a.startsAt)} · {formatTime(a.startsAt)}–{formatTime(a.endsAt)}
                </Muted>
              </div>
              <Badge $tone={APPOINTMENT_STATUS[a.status]?.tone}>{APPOINTMENT_STATUS[a.status]?.label}</Badge>
            </Head>

            <DetailList>
              <div>
                <dt>Profissional</dt>
                <dd>{a.professionalName}</dd>
              </div>
              <div>
                <dt>Procedimento</dt>
                <dd>{a.procedureName ?? "—"}</dd>
              </div>
              <div>
                <dt>Sala</dt>
                <dd>{a.roomName ?? "—"}</dd>
              </div>
              <div>
                <dt>Celular do paciente</dt>
                <dd>{formatPhone(a.patientMobile)}</dd>
              </div>
              {a.notes && (
                <div>
                  <dt>Observações</dt>
                  <dd>{a.notes}</dd>
                </div>
              )}
              {a.cancellationReason && (
                <div>
                  <dt>Motivo do cancelamento</dt>
                  <dd>{a.cancellationReason}</dd>
                </div>
              )}
              <div>
                <dt>Lembrete enviado</dt>
                <dd>{a.reminderSentAt ? formatDateTime(a.reminderSentAt) : "Ainda não"}</dd>
              </div>
            </DetailList>

            {writable && (APPOINTMENT_ACTIONS[a.status]?.length ?? 0) > 0 && (
              <Actions>
                {APPOINTMENT_ACTIONS[a.status]?.map((action) => (
                  <Button
                    key={action.status}
                    variant={action.status === "CANCELLED" || action.status === "NO_SHOW" ? "outline" : "primary"}
                    loading={changeStatus.isPending && changeStatus.variables?.status === action.status}
                    onClick={() =>
                      action.status === "CANCELLED"
                        ? setCancelling(true)
                        : changeStatus.mutate({ status: action.status })
                    }
                  >
                    {action.label}
                  </Button>
                ))}
                {!["COMPLETED", "CANCELLED"].includes(a.status) && (
                  <Button variant="ghost" onClick={() => onEdit(a)}>
                    Editar / remarcar
                  </Button>
                )}
                {["SCHEDULED", "CONFIRMED"].includes(a.status) && (
                  <WhatsAppButton
                    phone={a.patientMobile}
                    allowed={patient.data?.allowWhatsapp ?? false}
                    label="Lembrar pelo WhatsApp"
                    message={reminder}
                  />
                )}
              </Actions>
            )}

            <section>
              <SectionTitle>Histórico</SectionTitle>
              <History>
                {data.history.map((h, i) => (
                  <li key={i}>
                    <strong>{HISTORY_LABELS[h.action] ?? h.action}</strong>
                    {h.action === "STATUS_CHANGED" && (
                      <span>
                        {" "}
                        → {APPOINTMENT_STATUS[String(h.details.to)]?.label ?? String(h.details.to)}
                        {h.details.reason ? ` (${String(h.details.reason)})` : ""}
                      </span>
                    )}
                    <Muted>
                      {" "}
                      · {formatDateTime(h.occurredAt)} · {h.actorName ?? "Paciente (link)"}
                    </Muted>
                  </li>
                ))}
              </History>
            </section>
          </Stack>
        )}
      </Modal>
      <ReasonDialog
        open={cancelling}
        title="Cancelar atendimento"
        description="O horário será liberado na agenda e o paciente será avisado por e-mail (se permitir contato)."
        confirmLabel="Cancelar atendimento"
        loading={changeStatus.isPending}
        onConfirm={(reason) => changeStatus.mutate({ status: "CANCELLED", reason })}
        onClose={() => setCancelling(false)}
      />
    </>
  );
}

const Head = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: ${({ theme }) => theme.space[3]};
`;

const PatientName = styled(Link)`
  display: block;
  font-size: ${({ theme }) => theme.fontSizes.lg};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  color: ${({ theme }) => theme.colors.text};
`;

const Actions = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[2]};
`;

const SectionTitle = styled.h3`
  font-size: ${({ theme }) => theme.fontSizes.md};
  margin-bottom: ${({ theme }) => theme.space[2]};
`;

const History = styled.ol`
  margin: 0;
  padding-left: ${({ theme }) => theme.space[5]};
  display: grid;
  gap: ${({ theme }) => theme.space[1]};
  font-size: ${({ theme }) => theme.fontSizes.sm};
`;
