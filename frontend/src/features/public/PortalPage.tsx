"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { LogOut, Repeat } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Badge, Card, CardTitle, EmptyState, Muted, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow } from "@/components/ui/fields";
import { LoadingState } from "@/components/ui/LoadingState";
import { TextField } from "@/components/ui/TextField";
import { ApiError } from "@/lib/api/client";
import { formatDate, formatDateTime, formatTime } from "@/lib/format";
import { APPOINTMENT_STATUS } from "@/lib/labels";
import { useRequiredFields } from "@/lib/useRequiredFields";
import {
  type PatientAction,
  type PortalContact,
  type PortalMe,
  type PortalOverview,
  publicApi,
  type SignatureInput,
} from "./api";
import { PortalDocuments } from "./PortalDocuments";
import { PORTAL_ME_KEY } from "./PortalLoginForm";
import { ConsentText, PublicLayout } from "./PublicLayout";
import { SignConsentForm } from "./SignConsentForm";

const isUnauthenticated = (error: unknown) => error instanceof ApiError && error.status === 401;

export function PortalPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [choosing, setChoosing] = useState(false);
  const me = useQuery({ queryKey: PORTAL_ME_KEY, queryFn: publicApi.portalMe, retry: false });
  const active = me.data?.activeTenantId && me.data.activePatientId ? me.data : null;
  const overview = useQuery({
    queryKey: ["portal", me.data?.activeTenantId, me.data?.activePatientId],
    queryFn: publicApi.portal,
    enabled: Boolean(active) && !choosing,
    retry: false,
  });

  const unauthenticated = isUnauthenticated(me.error) || isUnauthenticated(overview.error);
  useEffect(() => {
    if (unauthenticated) router.replace("/portal/entrar");
  }, [unauthenticated, router]);

  const logout = useMutation({
    mutationFn: publicApi.portalLogout,
    onSettled: () => {
      queryClient.clear();
      router.replace("/portal/entrar");
    },
  });

  const data = overview.data;
  const showPicker = Boolean(me.data) && (!active || choosing);
  const actions = me.data && (
    <>
      {me.data.profiles.length > 1 && !showPicker && (
        <button type="button" onClick={() => setChoosing(true)}>
          <Repeat size={14} aria-hidden /> Trocar perfil
        </button>
      )}
      <button type="button" onClick={() => logout.mutate()} disabled={logout.isPending}>
        <LogOut size={14} aria-hidden /> Sair
      </button>
    </>
  );

  return (
    <PublicLayout
      clinicName={showPicker ? undefined : data?.clinicName}
      title={
        showPicker ? "Escolha o perfil" : data ? `Olá, ${data.patient.fullName.split(" ")[0]}` : "Portal do paciente"
      }
      actions={actions}
    >
      {me.error && !unauthenticated && <Alert tone="danger">{me.error.message}</Alert>}
      {overview.error && !unauthenticated && <Alert tone="danger">{overview.error.message}</Alert>}
      {(me.isPending || (active && !choosing && overview.isPending) || unauthenticated) && <LoadingState />}
      {showPicker && me.data && (
        <ProfilePicker
          me={me.data}
          onChosen={(next) => {
            queryClient.setQueryData(PORTAL_ME_KEY, next);
            queryClient.removeQueries({ queryKey: ["portal-records"] });
            setChoosing(false);
          }}
        />
      )}
      {!showPicker && data && (
        <Stack>
          <Appointments data={data} />
          <Consents data={data} />
          <PortalDocuments key={`${me.data?.activeTenantId}-${me.data?.activePatientId}`} />
          <Contact key={JSON.stringify(data.patient)} patient={data.patient} />
          {data.clinicPhone && <p>Dúvidas? Fale com a clínica pelo telefone {data.clinicPhone}.</p>}
        </Stack>
      )}
    </PublicLayout>
  );
}

function ProfilePicker({ me, onChosen }: { me: PortalMe; onChosen: (me: PortalMe) => void }) {
  const select = useMutation({
    mutationFn: (p: { tenantId: string; patientId: string }) => publicApi.portalSelectProfile(p.tenantId, p.patientId),
    onSuccess: onChosen,
  });
  if (me.profiles.length === 0) {
    return (
      <Card>
        <EmptyState
          title="Nenhum acesso disponível"
          description="O acesso ao portal foi bloqueado pela clínica. Em caso de dúvida, fale com a clínica."
        />
      </Card>
    );
  }
  return (
    <Card>
      <CardTitle>Você tem acesso a mais de um cadastro</CardTitle>
      {select.error && <Alert tone="danger">{select.error.message}</Alert>}
      <Profiles>
        {me.profiles.map((p) => (
          <li key={`${p.tenantId}-${p.patientId}`}>
            <div>
              <strong>{p.patientName}</strong>
              <Muted>{p.clinicName}</Muted>
            </div>
            <Button
              variant="outline"
              onClick={() => select.mutate({ tenantId: p.tenantId, patientId: p.patientId })}
              loading={select.isPending && select.variables?.patientId === p.patientId}
            >
              Entrar
            </Button>
          </li>
        ))}
      </Profiles>
    </Card>
  );
}

function usePortalMutation<V>(fn: (v: V) => Promise<PortalOverview>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (data) => {
      client.setQueriesData({ queryKey: ["portal"] }, data);
      void client.invalidateQueries({ queryKey: ["portal-records"] });
    },
  });
}

function Appointments({ data }: { data: PortalOverview }) {
  const respond = usePortalMutation((v: { id: string; action: PatientAction }) =>
    publicApi.portalRespond(v.id, v.action),
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

function Consents({ data }: { data: PortalOverview }) {
  const [open, setOpen] = useState<string | null>(null);
  const sign = usePortalMutation((v: { id: string; input: SignatureInput }) => publicApi.portalSign(v.id, v.input));
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

function Contact({ patient }: { patient: PortalContact }) {
  const [v, setV] = useState({
    mobile: patient.mobile ?? "",
    phone: patient.phone ?? "",
    allowEmail: patient.allowEmail,
    allowSms: patient.allowSms,
    allowWhatsapp: patient.allowWhatsapp,
  });
  const form = useRequiredFields({ mobile: !v.mobile.trim() && "Informe o celular." });
  const save = usePortalMutation(() =>
    publicApi.portalContact({ ...v, mobile: v.mobile.trim(), phone: v.phone.trim() || null }),
  );
  return (
    <Card>
      <CardTitle>Meus dados de contato</CardTitle>
      <FormGrid $columns={3}>
        <TextField
          label="E-mail de acesso"
          value={patient.email ?? ""}
          disabled
          readOnly
          hint="Para trocar o e-mail de acesso, fale com a clínica."
        />
        <TextField
          label="Celular"
          required
          maxLength={20}
          value={v.mobile}
          error={form.errors.mobile}
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
        <Button onClick={() => form.check() && save.mutate(undefined)} loading={save.isPending}>
          Salvar contato
        </Button>
      </Row>
    </Card>
  );
}

const Profiles = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: ${({ theme }) => theme.space[2]};

  li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: ${({ theme }) => theme.space[3]};
    padding: ${({ theme }) => theme.space[3]};
    border: 1px solid ${({ theme }) => theme.colors.border};
    border-radius: ${({ theme }) => theme.radii.md};
  }
  li div {
    display: grid;
    gap: 2px;
  }
`;
