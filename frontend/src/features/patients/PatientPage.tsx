"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, CalendarPlus, ClipboardPlus } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import styled from "styled-components";
import { ConfirmDialog } from "@/components/ui/Modal";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import {
  Badge,
  Card,
  CardTitle,
  DetailList,
  ErrorState,
  Muted,
  PageHeader,
  PermissionDenied,
  Row,
  Stack,
} from "@/components/ui/display";
import { CheckboxField } from "@/components/ui/fields";
import { LoadingState } from "@/components/ui/LoadingState";
import { Tabs } from "@/components/ui/Tabs";
import { useToast } from "@/components/ui/Toast";
import { AestheticsSection, useAestheticsEnabled } from "@/features/aesthetics/AestheticsPage";
import { AnamnesisSection } from "@/features/clinical/AnamnesisSection";
import { AttachmentsSection } from "@/features/clinical/AttachmentsSection";
import { ChartSection } from "@/features/clinical/ChartSection";
import { ConsentsSection } from "@/features/clinical/ConsentsSection";
import { DocumentsSection } from "@/features/clinical/DocumentsSection";
import { NotesSection, PerformedSection } from "@/features/clinical/NotesSection";
import { api } from "@/lib/api/endpoints";
import type { Appointment, PlanSummary, ReceivableSummary } from "@/lib/api/types";
import { ageFrom, formatCpf, formatDate, formatDateTime, formatMoney, formatPhone, formatTime } from "@/lib/format";
import { APPOINTMENT_STATUS, FINANCE_STATUS, PLAN_STATUS, SEX_OPTIONS } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { PatientForm, toInput } from "./PatientForm";

export function PatientPage({ patientId }: { patientId: string }) {
  const can = useCan();
  const router = useRouter();
  const params = useSearchParams();
  const [tab, setTab] = useState(params.get("aba") ?? "dados");
  const patient = useQuery({ queryKey: ["patient", patientId], queryFn: () => api.patients.get(patientId) });

  if (patient.isPending) return <LoadingState />;
  if (patient.error) return <ErrorState error={patient.error} onRetry={patient.refetch} />;
  const p = patient.data;
  const age = ageFrom(p.birthDate);

  const tabs = [
    { id: "dados", label: "Dados", content: <DataTab patientId={patientId} /> },
    can(P.CLINICAL_READ) && {
      id: "prontuario",
      label: "Prontuário",
      content: <RecordTab patientId={patientId} patientName={p.fullName} initialSection={params.get("secao")} />,
    },
    can(P.CLINICAL_READ, P.PATIENTS_WRITE) && {
      id: "termos",
      label: "Termos",
      content: <ConsentsSection patientId={patientId} patientName={p.fullName} />,
    },
    can(P.SCHEDULE_READ) && { id: "agenda", label: "Consultas", content: <AppointmentsTab patientId={patientId} /> },
    can(P.TREATMENTS_READ) && { id: "orcamentos", label: "Orçamentos", content: <PlansTab patientId={patientId} /> },
    can(P.FINANCE_READ) && { id: "financeiro", label: "Financeiro", content: <FinanceTab patientId={patientId} /> },
    can(P.PATIENTS_WRITE) && {
      id: "portal",
      label: "Portal",
      content: <PortalTab patientId={patientId} email={p.email} allowEmail={p.allowEmail} />,
    },
  ].filter(Boolean) as { id: string; label: string; content: React.ReactNode }[];

  return (
    <Stack>
      <BackLink href="/pacientes">
        <ArrowLeft size={16} aria-hidden /> Pacientes
      </BackLink>
      <PageHeader
        title={p.socialName ?? p.fullName}
        description={
          <span>
            {[
              age !== null ? `${age} anos` : null,
              p.cpf ? `CPF ${formatCpf(p.cpf)}` : null,
              formatPhone(p.mobile ?? p.phone),
            ]
              .filter(Boolean)
              .join(" · ")}{" "}
            {!p.active && <Badge>Inativo</Badge>}
          </span>
        }
        actions={
          <>
            {can(P.SCHEDULE_WRITE) && (
              <Button variant="outline" onClick={() => router.push(`/agenda?novo=1`)}>
                <CalendarPlus size={18} aria-hidden /> Agendar
              </Button>
            )}
            {can(P.TREATMENTS_WRITE) && (
              <Button onClick={() => router.push(`/tratamentos/novo?paciente=${patientId}`)}>
                <ClipboardPlus size={18} aria-hidden /> Novo orçamento
              </Button>
            )}
          </>
        }
      />
      <Tabs label="Seções do paciente" items={tabs} active={tab} onChange={setTab} />
    </Stack>
  );
}

function DataTab({ patientId }: { patientId: string }) {
  const can = useCan();
  const [editing, setEditing] = useState(false);
  const [toggling, setToggling] = useState(false);
  const { data: p } = useQuery({ queryKey: ["patient", patientId], queryFn: () => api.patients.get(patientId) });
  const update = useAction(
    (input: Parameters<typeof api.patients.update>[1]) => api.patients.update(patientId, input),
    {
      success: "Cadastro atualizado.",
      invalidate: [["patient", patientId], ["patients"]],
      onSuccess: () => setEditing(false),
    },
  );
  const toggle = useAction(() => api.patients.setActive(patientId, !p?.active), {
    success: p?.active ? "Paciente inativado." : "Paciente reativado.",
    invalidate: [["patient", patientId], ["patients"]],
    onSuccess: () => setToggling(false),
  });
  if (!p) return <LoadingState />;

  if (editing) {
    return (
      <Card>
        <PatientForm
          initial={toInput(p)}
          saving={update.isPending}
          serverError={update.error}
          submitLabel="Salvar alterações"
          onSubmit={(input) => update.mutate({ ...input, version: p.version })}
          onCancel={() => setEditing(false)}
        />
      </Card>
    );
  }

  const address = [p.street, p.number, p.complement, p.district, p.city && `${p.city}/${p.state ?? ""}`, p.zipCode]
    .filter(Boolean)
    .join(", ");

  return (
    <Stack $gap={4}>
      {can(P.PATIENTS_WRITE) && (
        <Row $justify="flex-end">
          <Button variant="ghost" onClick={() => setToggling(true)}>
            {p.active ? "Inativar paciente" : "Reativar paciente"}
          </Button>
          <Button variant="outline" onClick={() => setEditing(true)}>
            Editar cadastro
          </Button>
        </Row>
      )}
      <Card>
        <CardTitle>Dados pessoais</CardTitle>
        <DetailList>
          <Item label="Nome completo" value={p.fullName} />
          <Item label="Nome social" value={p.socialName} />
          <Item
            label="Nascimento"
            value={p.birthDate ? `${formatDate(p.birthDate)} (${ageFrom(p.birthDate)} anos)` : null}
          />
          <Item label="Sexo" value={SEX_OPTIONS.find((s) => s.value === p.sex)?.label} />
          <Item label="CPF" value={p.cpf ? formatCpf(p.cpf) : null} />
          <Item label="RG" value={p.rg} />
          <Item label="Profissão" value={p.occupation} />
          <Item label="Como conheceu" value={p.referralSource} />
        </DetailList>
      </Card>
      <Card>
        <CardTitle>Contato e endereço</CardTitle>
        <DetailList>
          <Item label="Celular" value={formatPhone(p.mobile)} />
          <Item label="Telefone" value={formatPhone(p.phone)} />
          <Item label="E-mail" value={p.email} />
          <Item
            label="Preferências de contato"
            value={
              [p.allowEmail && "E-mail", p.allowSms && "SMS", p.allowWhatsapp && "WhatsApp"]
                .filter(Boolean)
                .join(", ") || "Nenhuma"
            }
          />
          <Item label="Endereço" value={address} />
        </DetailList>
      </Card>
      <Card>
        <CardTitle>Convênio e responsável</CardTitle>
        <DetailList>
          <Item label="Convênio" value={p.healthInsurance} />
          <Item label="Carteirinha" value={p.insuranceNumber} />
          <Item label="Responsável legal" value={p.guardianName} />
          <Item label="CPF do responsável" value={p.guardianCpf ? formatCpf(p.guardianCpf) : null} />
          <Item label="Telefone do responsável" value={formatPhone(p.guardianPhone)} />
        </DetailList>
      </Card>
      {p.notes && (
        <Card>
          <CardTitle>Observações</CardTitle>
          <p style={{ whiteSpace: "pre-wrap" }}>{p.notes}</p>
        </Card>
      )}
      <Muted>
        Cadastrado em {formatDateTime(p.createdAt)} · atualizado em {formatDateTime(p.updatedAt)}
      </Muted>
      <ConfirmDialog
        open={toggling}
        title={p.active ? "Inativar paciente" : "Reativar paciente"}
        message={
          p.active
            ? "O paciente deixa de aparecer nas buscas padrão. O prontuário e o histórico são mantidos."
            : "O paciente volta a aparecer nas buscas."
        }
        confirmLabel={p.active ? "Inativar" : "Reativar"}
        tone={p.active ? "danger" : "primary"}
        loading={toggle.isPending}
        onConfirm={() => toggle.mutate(undefined)}
        onClose={() => setToggling(false)}
      />
    </Stack>
  );
}

function Item({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value || "—"}</dd>
    </div>
  );
}

function RecordTab({
  patientId,
  patientName,
  initialSection,
}: {
  patientId: string;
  patientName: string;
  initialSection: string | null;
}) {
  const [section, setSection] = useState(initialSection ?? "anamnese");
  const { enabled: aesthetics } = useAestheticsEnabled();
  const summary = useQuery({
    queryKey: ["record", patientId],
    queryFn: () => api.clinical.summary(patientId),
    staleTime: 0,
  });
  if (summary.error) return <PermissionDenied description={summary.error.message} />;
  return (
    <Stack $gap={4}>
      {summary.data && summary.data.alerts.length > 0 && (
        <Alert tone="danger" title="Alertas de saúde (anamnese)">
          {summary.data.alerts.map((a) => `${a.text}${a.detail ? `: ${a.detail}` : ""}`).join(" · ")}
        </Alert>
      )}
      {summary.data && !summary.data.lastAnamnesisAt && <Alert tone="warning">Paciente sem anamnese registrada.</Alert>}
      <Tabs
        label="Seções do prontuário"
        active={section}
        onChange={setSection}
        items={[
          { id: "anamnese", label: "Anamnese", content: <AnamnesisSection patientId={patientId} /> },
          { id: "odontograma", label: "Odontograma", content: <ChartSection patientId={patientId} /> },
          { id: "evolucoes", label: "Evoluções", content: <NotesSection patientId={patientId} /> },
          { id: "procedimentos", label: "Procedimentos", content: <PerformedSection patientId={patientId} /> },
          {
            id: "documentos",
            label: "Documentos e orientações",
            content: <DocumentsSection patientId={patientId} patientName={patientName} />,
          },
          { id: "anexos", label: "Anexos, fotos e exames", content: <AttachmentsSection patientId={patientId} /> },
          ...(aesthetics
            ? [
                {
                  id: "harmonizacao",
                  label: "Harmonização",
                  content: <AestheticsSection patientId={patientId} patientName={patientName} />,
                },
              ]
            : []),
        ]}
      />
    </Stack>
  );
}

function AppointmentsTab({ patientId }: { patientId: string }) {
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["appointments", "patient", patientId],
    queryFn: () => api.appointments.list({ patientId }),
  });
  return (
    <DataTable<Appointment>
      caption="Consultas do paciente"
      rows={data}
      loading={isPending}
      error={error}
      onRetry={refetch}
      rowKey={(a) => a.id}
      emptyTitle="Nenhuma consulta"
      columns={[
        { key: "date", header: "Data", render: (a) => `${formatDate(a.startsAt)} ${formatTime(a.startsAt)}` },
        { key: "prof", header: "Profissional", render: (a) => a.professionalName },
        { key: "proc", header: "Procedimento", hideOnMobile: true, render: (a) => a.procedureName ?? "—" },
        {
          key: "status",
          header: "Status",
          render: (a) => (
            <Badge $tone={APPOINTMENT_STATUS[a.status]?.tone}>{APPOINTMENT_STATUS[a.status]?.label}</Badge>
          ),
        },
      ]}
    />
  );
}

function PlansTab({ patientId }: { patientId: string }) {
  const router = useRouter();
  const { data, isPending } = useQuery({
    queryKey: ["plans", "patient", patientId, "all"],
    queryFn: () => api.plans.list({ patientId }),
  });
  return (
    <DataTable<PlanSummary>
      caption="Orçamentos do paciente"
      rows={data}
      loading={isPending}
      rowKey={(p) => p.id}
      onRowClick={(p) => router.push(`/tratamentos/${p.id}`)}
      emptyTitle="Nenhum orçamento"
      columns={[
        { key: "date", header: "Criado em", render: (p) => formatDate(p.createdAt) },
        { key: "title", header: "Título", render: (p) => <strong>{p.title}</strong> },
        { key: "total", header: "Total", align: "right", render: (p) => formatMoney(p.total) },
        { key: "progress", header: "Realizado", render: (p) => `${p.itemsDone}/${p.itemsTotal}` },
        {
          key: "status",
          header: "Status",
          render: (p) => <Badge $tone={PLAN_STATUS[p.status]?.tone}>{PLAN_STATUS[p.status]?.label}</Badge>,
        },
      ]}
    />
  );
}

function FinanceTab({ patientId }: { patientId: string }) {
  const router = useRouter();
  const { data, isPending } = useQuery({
    queryKey: ["receivables", "patient", patientId],
    queryFn: () => api.finance.receivables({ patientId }),
  });
  const balance = (data ?? []).reduce((sum, r) => sum + Number(r.balance), 0);
  return (
    <Stack $gap={3}>
      <Muted>Saldo em aberto: {formatMoney(balance)}</Muted>
      <DataTable<ReceivableSummary>
        caption="Contas do paciente"
        rows={data}
        loading={isPending}
        rowKey={(r) => r.id}
        onRowClick={(r) => router.push(`/financeiro?conta=${r.id}`)}
        emptyTitle="Nenhuma conta"
        columns={[
          { key: "desc", header: "Descrição", render: (r) => r.description },
          { key: "total", header: "Total", align: "right", render: (r) => formatMoney(r.total) },
          { key: "paid", header: "Pago", align: "right", render: (r) => formatMoney(r.paid) },
          { key: "balance", header: "Saldo", align: "right", render: (r) => formatMoney(r.balance) },
          {
            key: "status",
            header: "Situação",
            render: (r) =>
              r.overdueInstallments > 0 ? (
                <Badge $tone="danger">{r.overdueInstallments} em atraso</Badge>
              ) : (
                <Badge $tone={FINANCE_STATUS[r.status]?.tone}>{FINANCE_STATUS[r.status]?.label}</Badge>
              ),
          },
        ]}
      />
    </Stack>
  );
}

function PortalTab({ patientId, email, allowEmail }: { patientId: string; email: string | null; allowEmail: boolean }) {
  const toast = useToast();
  const [sendEmail, setSendEmail] = useState(Boolean(email && allowEmail));
  const [url, setUrl] = useState<string | null>(null);
  const grant = useAction(() => api.patients.portalAccess(patientId, sendEmail), {
    success: "Link do portal gerado.",
    onSuccess: (r) => setUrl(r.url),
  });
  const revoke = useAction(() => api.patients.revokePortal(patientId), {
    success: "Acesso ao portal revogado.",
    onSuccess: () => setUrl(null),
  });
  return (
    <Card>
      <Stack $gap={4}>
        <p>
          O portal permite ao paciente ver as próximas consultas, confirmar ou cancelar, assinar termos pendentes e
          atualizar o contato. O acesso é por link pessoal válido por 30 dias; gerar um novo link revoga o anterior.
        </p>
        <CheckboxField
          label={email ? `Enviar o link para ${email}` : "Paciente sem e-mail cadastrado"}
          checked={sendEmail}
          disabled={!email || !allowEmail}
          onChange={(e) => setSendEmail(e.target.checked)}
        />
        {email && !allowEmail && <Muted>O paciente não autorizou contato por e-mail.</Muted>}
        <Row>
          <Button onClick={() => grant.mutate(undefined)} loading={grant.isPending}>
            Gerar link do portal
          </Button>
          <Button variant="ghost" onClick={() => revoke.mutate(undefined)} loading={revoke.isPending}>
            Revogar acesso
          </Button>
        </Row>
        {url && (
          <Alert tone="success" title="Link gerado">
            <code style={{ wordBreak: "break-all" }}>{url}</code>{" "}
            <Button
              variant="ghost"
              onClick={() => navigator.clipboard.writeText(url).then(() => toast.success("Link copiado."))}
            >
              Copiar
            </Button>
          </Alert>
        )}
        {grant.error && <Alert tone="danger">{grant.error.message}</Alert>}
      </Stack>
    </Card>
  );
}

const BackLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: ${({ theme }) => theme.fontSizes.sm};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  text-decoration: none;
`;
