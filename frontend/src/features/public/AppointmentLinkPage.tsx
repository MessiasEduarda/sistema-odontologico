"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Badge, Card, DetailList, Row } from "@/components/ui/display";
import { LoadingState } from "@/components/ui/LoadingState";
import { formatDate, formatTime } from "@/lib/format";
import { APPOINTMENT_STATUS } from "@/lib/labels";
import { useHashToken } from "@/lib/useHashToken";
import { type PatientAction, publicApi } from "./api";
import { PublicLayout } from "./PublicLayout";

export function AppointmentLinkPage() {
  const token = useHashToken();
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ["public-appointment", token],
    queryFn: () => publicApi.appointment(token ?? ""),
    enabled: Boolean(token),
    retry: false,
  });
  const respond = useMutation({
    mutationFn: (action: PatientAction) => publicApi.respondAppointment(token ?? "", action),
    onSuccess: (data) => client.setQueryData(["public-appointment", token], data),
  });
  const a = query.data;
  return (
    <PublicLayout clinicName={a?.clinicName} title="Sua consulta">
      {(token === "" || query.isError) && (
        <Alert tone="danger">{query.error?.message ?? "Este link é inválido ou expirou."}</Alert>
      )}
      {(token === null || (token && query.isPending)) && <LoadingState />}
      {a && (
        <Card>
          <p>Olá, {a.patientFirstName}.</p>
          <DetailList>
            <div>
              <dt>Data</dt>
              <dd>{formatDate(a.startsAt)}</dd>
            </div>
            <div>
              <dt>Horário</dt>
              <dd>
                {formatTime(a.startsAt)} às {formatTime(a.endsAt)}
              </dd>
            </div>
            <div>
              <dt>Profissional</dt>
              <dd>{a.professionalName}</dd>
            </div>
            <div>
              <dt>Situação</dt>
              <dd>
                <Badge $tone={APPOINTMENT_STATUS[a.status]?.tone}>
                  {APPOINTMENT_STATUS[a.status]?.label ?? a.status}
                </Badge>
              </dd>
            </div>
          </DetailList>
          {respond.error && <Alert tone="danger">{respond.error.message}</Alert>}
          {respond.isSuccess && (
            <Alert tone="success">
              {a.status === "CANCELLED"
                ? "Consulta cancelada. A clínica foi avisada."
                : "Presença confirmada. Obrigado!"}
            </Alert>
          )}
          {a.canRespond && (
            <Row $gap={3} style={{ marginTop: 16 }}>
              {a.status !== "CONFIRMED" && (
                <Button
                  onClick={() => respond.mutate("CONFIRM")}
                  loading={respond.isPending && respond.variables === "CONFIRM"}
                >
                  Confirmar presença
                </Button>
              )}
              <Button
                variant="outline"
                onClick={() => respond.mutate("CANCEL")}
                loading={respond.isPending && respond.variables === "CANCEL"}
              >
                Não poderei ir (cancelar)
              </Button>
            </Row>
          )}
          {!a.canRespond && !respond.isSuccess && (
            <Alert tone="info">
              Esta consulta não pode mais ser alterada por aqui. Para remarcar, fale com a clínica.
            </Alert>
          )}
        </Card>
      )}
    </PublicLayout>
  );
}
