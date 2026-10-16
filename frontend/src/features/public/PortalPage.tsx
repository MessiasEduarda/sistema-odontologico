"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Badge, Card, CardTitle, EmptyState, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow } from "@/components/ui/fields";
import { LoadingState } from "@/components/ui/LoadingState";
import { TextField } from "@/components/ui/TextField";
import { formatDate, formatDateTime, formatTime } from "@/lib/format";
import { APPOINTMENT_STATUS } from "@/lib/labels";
import { useHashToken } from "@/lib/useHashToken";
import { type PatientAction, type PortalContact, type PortalOverview, publicApi, type SignatureInput } from "./api";
import { ConsentText, PublicLayout } from "./PublicLayout";
import { SignConsentForm } from "./SignConsentForm";

/** Portal do paciente: próximas consultas, termos pendentes e dados de contato. */
export function PortalPage() {
  const token = useHashToken();
  const query = useQuery({
    queryKey: ["portal", token],
    queryFn: () => publicApi.portal(token ?? ""),
    enabled: Boolean(token),
    retry: false,
  });
  const data = query.data;
  return (
    <PublicLayout
      clinicName={data?.clinicName}
      title={data ? `Olá, ${data.patient.fullName.split(" ")[0]}` : "Portal do paciente"}
    >
      {(token === "" || query.isError) && (
        <Alert tone="danger">
          {query.error?.message ?? "Este link de acesso é inválido ou foi revogado. Peça um novo à clínica."}
        </Alert>
      )}
      {(token === null || (token && query.isPending)) && <LoadingState />}
      {data && token && (
        <Stack>
          <Appointments token={token} data={data} />
          <Consents token={token} data={data} />
          <Contact key={JSON.stringify(data.patient)} token={token} patient={data.patient} />
          {data.clinicPhone && <p>Dúvidas? Fale com a clínica pelo telefone {data.clinicPhone}.</p>}
        </Stack>
      )}
    </PublicLayout>
  );
}

function usePortalMutation<V>(token: string, fn: (v: V) => Promise<PortalOverview>) {
  const client = useQueryClient();
  return useMutation({ mutationFn: fn, onSuccess: (data) => client.setQueryData(["portal", token], data) });
}

function Appointments({ token, data }: { token: string; data: PortalOverview }) {
  const respond = usePortalMutation(token, (v: { id: string; action: PatientAction }) =>
    publicApi.portalRespond(token, v.id, v.action),
  );
  return (
    <Card>
      <CardTitle>Próximas consultas</CardTitle>
      {respond.error && <Alert tone="danger">{respond.error.message}</Alert>}
      {data.upcoming.length === 0 ? (
        <EmptyState title="Nenhuma consulta agendada" />
      ) : (
        <Stack $gap={3}>
          {data.upcoming.map((a) => (
            <Row key={a.id} $justify="space-between">
              <div>
                <strong>
                  {formatDate(a.startsAt)} · {formatTime(a.startsAt)}
                </strong>
                <br />
                <small>{a.professionalName}</small>{" "}
                <Badge $tone={APPOINTMENT_STATUS[a.status]?.tone}>
                  {APPOINTMENT_STATUS[a.status]?.label ?? a.status}
                </Badge>
              </div>
              {a.canRespond && (
                <Row $gap={2}>
                  {a.status !== "CONFIRMED" && (
                    <Button
                      onClick={() => respond.mutate({ id: a.id, action: "CONFIRM" })}
                      loading={
                        respond.isPending && respond.variables?.id === a.id && respond.variables.action === "CONFIRM"
                      }
                    >
                      Confirmar
                    </Button>
                  )}
                  <Button
                    variant="outline"
                    onClick={() => respond.mutate({ id: a.id, action: "CANCEL" })}
                    loading={
                      respond.isPending && respond.variables?.id === a.id && respond.variables.action === "CANCEL"
                    }
                  >
                    Cancelar
                  </Button>
                </Row>
              )}
            </Row>
          ))}
        </Stack>
      )}
    </Card>
  );
}

function Consents({ token, data }: { token: string; data: PortalOverview }) {
  const [open, setOpen] = useState<string | null>(null);
  const sign = usePortalMutation(token, (v: { id: string; input: SignatureInput }) =>
    publicApi.portalSign(token, v.id, v.input),
  );
  if (data.pendingConsents.length === 0 && !sign.isSuccess) return null;
  return (
    <Card>
      <CardTitle>Termos para assinar</CardTitle>
      {sign.isSuccess && <Alert tone="success">Termo assinado. Obrigado!</Alert>}
      <Stack $gap={4}>
        {data.pendingConsents.map((c) => (
          <div key={c.id}>
            <Row $justify="space-between">
              <div>
                <strong>{c.title}</strong>
                <br />
                <small>Enviado em {formatDateTime(c.createdAt)}</small>
              </div>
              <Button variant="outline" onClick={() => setOpen(open === c.id ? null : c.id)}>
                {open === c.id ? "Fechar" : "Ler e assinar"}
              </Button>
            </Row>
            {open === c.id && (
              <Stack $gap={4} style={{ marginTop: 12 }}>
                <ConsentText>{c.body}</ConsentText>
                <SignConsentForm
                  defaultName={data.patient.fullName}
                  loading={sign.isPending}
                  error={sign.error?.message}
                  onSign={(input) => sign.mutate({ id: c.id, input }, { onSuccess: () => setOpen(null) })}
                />
              </Stack>
            )}
          </div>
        ))}
      </Stack>
    </Card>
  );
}

function Contact({ token, patient }: { token: string; patient: PortalContact }) {
  const [v, setV] = useState({
    email: patient.email ?? "",
    mobile: patient.mobile ?? "",
    phone: patient.phone ?? "",
    allowEmail: patient.allowEmail,
    allowSms: patient.allowSms,
    allowWhatsapp: patient.allowWhatsapp,
  });
  const save = usePortalMutation(token, () =>
    publicApi.portalContact(token, {
      ...v,
      email: v.email.trim() || null,
      mobile: v.mobile.trim() || null,
      phone: v.phone.trim() || null,
    }),
  );
  return (
    <Card>
      <CardTitle>Meus dados de contato</CardTitle>
      <FormGrid $columns={3}>
        <TextField
          label="E-mail"
          type="email"
          maxLength={254}
          value={v.email}
          onChange={(e) => setV({ ...v, email: e.target.value })}
        />
        <TextField
          label="Celular"
          maxLength={20}
          value={v.mobile}
          onChange={(e) => setV({ ...v, mobile: e.target.value })}
        />
        <TextField
          label="Telefone"
          maxLength={20}
          value={v.phone}
          onChange={(e) => setV({ ...v, phone: e.target.value })}
        />
        <FullRow>
          <Row $gap={4}>
            <CheckboxField
              label="Aceito receber avisos por e-mail"
              checked={v.allowEmail}
              onChange={(e) => setV({ ...v, allowEmail: e.target.checked })}
            />
            <CheckboxField
              label="Aceito SMS"
              checked={v.allowSms}
              onChange={(e) => setV({ ...v, allowSms: e.target.checked })}
            />
            <CheckboxField
              label="Aceito WhatsApp"
              checked={v.allowWhatsapp}
              onChange={(e) => setV({ ...v, allowWhatsapp: e.target.checked })}
            />
          </Row>
        </FullRow>
        {save.error && (
          <FullRow>
            <Alert tone="danger">{save.error.message}</Alert>
          </FullRow>
        )}
        {save.isSuccess && (
          <FullRow>
            <Alert tone="success">Dados atualizados.</Alert>
          </FullRow>
        )}
      </FormGrid>
      <Row $justify="flex-end" style={{ marginTop: 16 }}>
        <Button onClick={() => save.mutate(undefined)} loading={save.isPending}>
          Salvar contato
        </Button>
      </Row>
    </Card>
  );
}
