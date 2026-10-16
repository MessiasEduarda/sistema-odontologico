"use client";

import { useQuery } from "@tanstack/react-query";
import { Building2, CreditCard, Plus, Users } from "lucide-react";
import { useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, KpiCard, KpiGrid, PageHeader, PermissionDenied, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow, SelectField, TextAreaField } from "@/components/ui/fields";
import { LoadingState } from "@/components/ui/LoadingState";
import { ConfirmDialog, Modal } from "@/components/ui/Modal";
import { SearchInput } from "@/components/ui/SearchInput";
import { Tabs } from "@/components/ui/Tabs";
import { TextField } from "@/components/ui/TextField";
import { useMe } from "@/features/auth/hooks";
import { api } from "@/lib/api/endpoints";
import type { PlatformClinic, PlatformEvent, PlatformPlan } from "@/lib/api/types";
import { formatDate, formatDateTime, formatMoney } from "@/lib/format";
import { FEATURE_LABELS, SUBSCRIPTION_STATUS } from "@/lib/labels";
import { useAction } from "@/lib/mutations";

const CLINIC_STATUS: Record<string, { label: string; tone: "success" | "danger" | "neutral" }> = {
  ACTIVE: { label: "Ativa", tone: "success" },
  SUSPENDED: { label: "Suspensa", tone: "danger" },
  CANCELLED: { label: "Cancelada", tone: "neutral" },
};

/** Administração do SaaS: só para administradores da plataforma; não acessa dados de pacientes. */
export function PlatformPage() {
  const { data: me } = useMe();
  const [tab, setTab] = useState("clinicas");
  if (me && !me.platformAdmin) return <PermissionDenied description="Área exclusiva da administração da plataforma." />;
  return (
    <Stack>
      <PageHeader title="Plataforma" description="Clínicas, planos, assinaturas e auditoria administrativa." />
      <Metrics />
      <Tabs
        label="Plataforma"
        active={tab}
        onChange={setTab}
        items={[
          { id: "clinicas", label: "Clínicas", content: <ClinicsTab /> },
          { id: "planos", label: "Planos", content: <PlansTab /> },
          { id: "auditoria", label: "Auditoria", content: <AuditTab /> },
        ]}
      />
    </Stack>
  );
}

function Metrics() {
  const { data } = useQuery({ queryKey: ["platform-metrics"], queryFn: api.platform.metrics });
  if (!data) return null;
  return (
    <KpiGrid>
      <KpiCard
        icon={Building2}
        label="Clínicas ativas"
        value={`${data.activeTenants} de ${data.tenants}`}
        hint={`${data.suspendedTenants} suspensa(s)`}
      />
      <KpiCard
        icon={CreditCard}
        label="Receita mensal recorrente"
        value={formatMoney(data.monthlyRecurringRevenue)}
        tone="success"
      />
      <KpiCard
        icon={CreditCard}
        label="Assinaturas"
        value={`${data.payingSubscriptions} pagantes`}
        hint={`${data.trialing} em avaliação · ${data.pastDue} inadimplente(s)`}
        tone={data.pastDue > 0 ? "danger" : "neutral"}
      />
      <KpiCard
        icon={Users}
        label="Usuários ativos"
        value={data.users}
        hint={`${data.activeSessions24h} conectado(s) nas últimas 24h`}
      />
    </KpiGrid>
  );
}

function ClinicsTab() {
  const [q, setQ] = useState("");
  const [creating, setCreating] = useState(false);
  const [subscription, setSubscription] = useState<PlatformClinic | null>(null);
  const [resending, setResending] = useState<PlatformClinic | null>(null);
  const [statusChange, setStatusChange] = useState<{ clinic: PlatformClinic; status: string } | null>(null);
  const clinics = useQuery({ queryKey: ["platform-clinics", q], queryFn: () => api.platform.clinics(q) });
  const setStatus = useAction(
    () => api.platform.setClinicStatus(statusChange?.clinic.id ?? "", statusChange?.status ?? ""),
    {
      success: "Situação da clínica atualizada com sucesso!",
      invalidate: [["platform-clinics"], ["platform-metrics"]],
      onSuccess: () => setStatusChange(null),
    },
  );
  return (
    <Stack $gap={4}>
      <Row $justify="space-between">
        <SearchInput label="Buscar clínica" placeholder="Nome ou identificador…" value={q} onChange={setQ} />
        <Button onClick={() => setCreating(true)}>
          <Plus size={16} aria-hidden /> Nova clínica
        </Button>
      </Row>
      <DataTable<PlatformClinic>
        caption="Clínicas"
        rows={clinics.data}
        loading={clinics.isPending}
        error={clinics.error}
        onRetry={clinics.refetch}
        rowKey={(c) => c.id}
        emptyTitle="Nenhuma clínica"
        columns={[
          {
            key: "name",
            header: "Clínica",
            render: (c) => (
              <span>
                <strong>{c.name}</strong>
                <br />
                <small>{c.slug}</small>
              </span>
            ),
          },
          {
            key: "status",
            header: "Situação",
            render: (c) => <Badge $tone={CLINIC_STATUS[c.status]?.tone}>{CLINIC_STATUS[c.status]?.label}</Badge>,
          },
          { key: "plan", header: "Plano", render: (c) => c.planName ?? "—" },
          {
            key: "sub",
            header: "Assinatura",
            render: (c) =>
              c.subscriptionStatus ? (
                <Badge $tone={SUBSCRIPTION_STATUS[c.subscriptionStatus]?.tone}>
                  {SUBSCRIPTION_STATUS[c.subscriptionStatus]?.label}
                </Badge>
              ) : (
                "—"
              ),
          },
          { key: "created", header: "Criada em", hideOnMobile: true, render: (c) => formatDate(c.createdAt) },
          {
            key: "actions",
            header: "",
            render: (c) => (
              <Row $gap={2} $wrap={false}>
                <Button variant="ghost" onClick={() => setSubscription(c)}>
                  Assinatura
                </Button>
                <Button variant="ghost" onClick={() => setResending(c)}>
                  Reenviar convite
                </Button>
                {c.status === "ACTIVE" ? (
                  <Button variant="ghost" onClick={() => setStatusChange({ clinic: c, status: "SUSPENDED" })}>
                    Suspender
                  </Button>
                ) : (
                  <Button variant="ghost" onClick={() => setStatusChange({ clinic: c, status: "ACTIVE" })}>
                    Reativar
                  </Button>
                )}
              </Row>
            ),
          },
        ]}
      />
      {creating && <NewClinic onClose={() => setCreating(false)} />}
      {resending && <ResendOwnerInvite clinic={resending} onClose={() => setResending(null)} />}
      {subscription && <SubscriptionDialog clinic={subscription} onClose={() => setSubscription(null)} />}
      <ConfirmDialog
        open={Boolean(statusChange)}
        question={
          statusChange?.status === "SUSPENDED"
            ? "Tem certeza que deseja suspender esta clínica?"
            : "Tem certeza que deseja reativar esta clínica?"
        }
        message={
          statusChange?.status === "SUSPENDED"
            ? `A equipe da ${statusChange.clinic.name} perderá o acesso até a reativação. Nenhum dado é apagado.`
            : `A equipe da ${statusChange?.clinic.name} volta a ter acesso.`
        }
        confirmLabel={statusChange?.status === "SUSPENDED" ? "Suspender" : "Reativar"}
        tone={statusChange?.status === "SUSPENDED" ? "danger" : "primary"}
        loading={setStatus.isPending}
        onConfirm={() => setStatus.mutate(undefined)}
        onClose={() => setStatusChange(null)}
      />
    </Stack>
  );
}

/** Reenvia o convite do responsável (e-mail não chegou, foi para o spam ou estava errado). */
function ResendOwnerInvite({ clinic, onClose }: { clinic: PlatformClinic; onClose: () => void }) {
  const [email, setEmail] = useState("");
  const resend = useAction(() => api.platform.resendOwnerInvite(clinic.id, email), {
    success: `Convite reenviado com sucesso! O link anterior deixou de valer.`,
    onSuccess: onClose,
  });
  const valid = /^\S+@\S+\.\S+$/.test(email);
  return (
    <Modal
      open
      onClose={onClose}
      title={`Reenviar convite — ${clinic.name}`}
      size="sm"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => resend.mutate(undefined)} loading={resend.isPending} disabled={!valid}>
            Reenviar
          </Button>
        </>
      }
    >
      <FormGrid $columns={1}>
        <TextField
          label="E-mail do responsável"
          type="email"
          autoComplete="off"
          value={email}
          onChange={(e) => setEmail(e.target.value.trim())}
          hint="Pode corrigir o endereço se o anterior estava errado. A pessoa recebe um link novo, válido por 7 dias."
        />
      </FormGrid>
    </Modal>
  );
}

function NewClinic({ onClose }: { onClose: () => void }) {
  const plans = useQuery({ queryKey: ["platform-plans"], queryFn: api.platform.plans });
  const [v, setV] = useState({ name: "", slug: "", ownerEmail: "", planCode: "PROFISSIONAL" });
  const create = useAction(() => api.platform.createClinic(v), {
    success: "Clínica criada com sucesso! O responsável recebeu o convite por e-mail.",
    invalidate: [["platform-clinics"], ["platform-metrics"]],
    onSuccess: onClose,
  });
  const slugify = (s: string) =>
    s
      .normalize("NFD")
      .replace(/[̀-ͯ]/g, "")
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-|-$/g, "");
  return (
    <Modal
      open
      onClose={onClose}
      title="Nova clínica"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => create.mutate(undefined)}
            loading={create.isPending}
            disabled={!v.name.trim() || v.slug.length < 3 || !/^\S+@\S+\.\S+$/.test(v.ownerEmail)}
          >
            Criar clínica
          </Button>
        </>
      }
    >
      <FormGrid $columns={1}>
        <TextField
          label="Nome"
          required
          maxLength={160}
          value={v.name}
          onChange={(e) => setV({ ...v, name: e.target.value, slug: slugify(e.target.value) })}
        />
        <TextField
          label="Identificador"
          required
          maxLength={60}
          value={v.slug}
          onChange={(e) => setV({ ...v, slug: slugify(e.target.value) })}
          hint="Letras minúsculas, números e hífens."
        />
        <TextField
          label="E-mail do responsável"
          type="email"
          required
          value={v.ownerEmail}
          onChange={(e) => setV({ ...v, ownerEmail: e.target.value })}
        />
        <SelectField
          label="Plano (30 dias de avaliação)"
          value={v.planCode}
          onChange={(e) => setV({ ...v, planCode: e.target.value })}
          options={(plans.data ?? []).filter((p) => p.active).map((p) => ({ value: p.code, label: p.name }))}
        />
        {create.error && <Alert tone="danger">{create.error.message}</Alert>}
      </FormGrid>
    </Modal>
  );
}

function SubscriptionDialog({ clinic, onClose }: { clinic: PlatformClinic; onClose: () => void }) {
  const plans = useQuery({ queryKey: ["platform-plans"], queryFn: api.platform.plans });
  const current = useQuery({
    queryKey: ["platform-subscription", clinic.id],
    queryFn: () => api.platform.subscription(clinic.id),
    retry: false,
  });
  const [draft, setDraft] = useState<{
    planCode: string;
    status: string;
    trialEndsAt: string;
    currentPeriodEnd: string;
    notes: string;
    paymentUrl: string;
  } | null>(null);
  const v = draft ?? {
    planCode: current.data?.planCode ?? "PROFISSIONAL",
    status: current.data?.status ?? "TRIALING",
    trialEndsAt: current.data?.trialEndsAt ?? "",
    currentPeriodEnd: current.data?.currentPeriodEnd ?? "",
    notes: current.data?.notes ?? "",
    paymentUrl: current.data?.paymentUrl ?? "",
  };
  const save = useAction(
    () =>
      api.platform.updateSubscription(clinic.id, {
        ...v,
        trialEndsAt: v.trialEndsAt || null,
        currentPeriodEnd: v.currentPeriodEnd || null,
        paymentUrl: v.paymentUrl.trim() || null,
      }),
    {
      success: "Assinatura atualizada com sucesso!",
      invalidate: [["platform-clinics"], ["platform-metrics"]],
      onSuccess: onClose,
    },
  );
  return (
    <Modal
      open
      onClose={onClose}
      title={`Assinatura: ${clinic.name}`}
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => save.mutate(undefined)} loading={save.isPending}>
            Salvar
          </Button>
        </>
      }
    >
      {current.isPending ? (
        <LoadingState />
      ) : (
        <FormGrid $columns={2}>
          <SelectField
            label="Plano"
            value={v.planCode}
            onChange={(e) => setDraft({ ...v, planCode: e.target.value })}
            options={(plans.data ?? []).map((p) => ({
              value: p.code,
              label: `${p.name} (${formatMoney(p.priceMonthly)}/mês)`,
            }))}
          />
          <SelectField
            label="Situação"
            value={v.status}
            onChange={(e) => setDraft({ ...v, status: e.target.value })}
            options={Object.entries(SUBSCRIPTION_STATUS).map(([value, s]) => ({ value, label: s.label }))}
          />
          <TextField
            label="Fim da avaliação"
            type="date"
            value={v.trialEndsAt}
            onChange={(e) => setDraft({ ...v, trialEndsAt: e.target.value })}
          />
          <TextField
            label="Fim do período pago"
            type="date"
            value={v.currentPeriodEnd}
            onChange={(e) => setDraft({ ...v, currentPeriodEnd: e.target.value })}
          />
          <FullRow>
            <TextField
              label="Link de pagamento"
              type="url"
              placeholder="https://… (PIX, boleto ou link de cobrança)"
              maxLength={500}
              value={v.paymentUrl}
              onChange={(e) => setDraft({ ...v, paymentUrl: e.target.value })}
            />
          </FullRow>
          <FullRow>
            <TextAreaField
              label="Observações (cobrança)"
              rows={2}
              maxLength={500}
              value={v.notes}
              onChange={(e) => setDraft({ ...v, notes: e.target.value })}
            />
          </FullRow>
          <FullRow>
            <Alert tone="info">
              Com o pagamento vencido (inadimplente, fim do período pago ou da avaliação), a clínica vê um aviso
              com o botão “Pagar agora”. Passados 5 dias, o acesso fica bloqueado até você registrar o pagamento
              (situação “Ativa” com novo fim de período). Cancelada bloqueia de vez.
            </Alert>
          </FullRow>
          {save.error && (
            <FullRow>
              <Alert tone="danger">{save.error.message}</Alert>
            </FullRow>
          )}
        </FormGrid>
      )}
    </Modal>
  );
}

const ALL_FEATURES = Object.keys(FEATURE_LABELS);

function PlansTab() {
  const plans = useQuery({ queryKey: ["platform-plans"], queryFn: api.platform.plans });
  const [editing, setEditing] = useState<PlatformPlan | "new" | null>(null);
  return (
    <Stack $gap={4}>
      <Row $justify="flex-end">
        <Button onClick={() => setEditing("new")}>
          <Plus size={16} aria-hidden /> Novo plano
        </Button>
      </Row>
      <DataTable<PlatformPlan>
        caption="Planos"
        rows={plans.data}
        loading={plans.isPending}
        rowKey={(p) => p.id}
        onRowClick={setEditing}
        columns={[
          { key: "name", header: "Plano", render: (p) => <strong>{p.name}</strong> },
          { key: "price", header: "Preço/mês", align: "right", render: (p) => formatMoney(p.priceMonthly) },
          {
            key: "limits",
            header: "Limites",
            render: (p) => `${p.maxProfessionals ?? "∞"} prof. · ${p.maxPatients ?? "∞"} pac.`,
          },
          {
            key: "features",
            header: "Recursos",
            hideOnMobile: true,
            render: (p) => p.features.map((f) => FEATURE_LABELS[f] ?? f).join(", "),
          },
          { key: "tenants", header: "Clínicas", align: "right", render: (p) => p.tenants },
          {
            key: "status",
            header: "Situação",
            render: (p) => (p.active ? <Badge $tone="success">Ativo</Badge> : <Badge>Inativo</Badge>),
          },
        ]}
      />
      {editing && <PlanForm plan={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </Stack>
  );
}

function PlanForm({ plan, onClose }: { plan: PlatformPlan | null; onClose: () => void }) {
  const [v, setV] = useState({
    code: plan?.code ?? "",
    name: plan?.name ?? "",
    priceMonthly: Number(plan?.priceMonthly ?? 0),
    maxProfessionals: plan?.maxProfessionals ?? null,
    maxPatients: plan?.maxPatients ?? null,
    features: plan?.features ?? ["AGENDA", "PATIENTS", "CLINICAL", "TREATMENTS", "FINANCE"],
    active: plan?.active ?? true,
  });
  const save = useAction(() => (plan ? api.platform.updatePlan(plan.id, v) : api.platform.createPlan(v)), {
    success: "Plano salvo com sucesso!",
    invalidate: [["platform-plans"]],
    onSuccess: onClose,
  });
  const toggle = (f: string) =>
    setV((s) => ({ ...s, features: s.features.includes(f) ? s.features.filter((x) => x !== f) : [...s.features, f] }));
  const limit = (value: string) => (value === "" ? null : Math.max(1, Number(value)));
  return (
    <Modal
      open
      onClose={onClose}
      title={plan ? `Editar plano ${plan.name}` : "Novo plano"}
      size="lg"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => save.mutate(undefined)}
            loading={save.isPending}
            disabled={!v.name.trim() || !/^[A-Z0-9_]{2,40}$/.test(v.code)}
          >
            Salvar
          </Button>
        </>
      }
    >
      <FormGrid $columns={3}>
        <TextField
          label="Código"
          required
          value={v.code}
          onChange={(e) => setV({ ...v, code: e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, "") })}
        />
        <TextField
          label="Nome"
          required
          maxLength={80}
          value={v.name}
          onChange={(e) => setV({ ...v, name: e.target.value })}
        />
        <TextField
          label="Preço mensal (R$)"
          type="number"
          min={0}
          step="0.01"
          value={v.priceMonthly}
          onChange={(e) => setV({ ...v, priceMonthly: Number(e.target.value) })}
        />
        <TextField
          label="Máx. profissionais"
          type="number"
          min={1}
          value={v.maxProfessionals ?? ""}
          hint="Vazio = ilimitado"
          onChange={(e) => setV({ ...v, maxProfessionals: limit(e.target.value) })}
        />
        <TextField
          label="Máx. pacientes ativos"
          type="number"
          min={1}
          value={v.maxPatients ?? ""}
          hint="Vazio = ilimitado"
          onChange={(e) => setV({ ...v, maxPatients: limit(e.target.value) })}
        />
        <CheckboxField
          label="Disponível para venda"
          checked={v.active}
          onChange={(e) => setV({ ...v, active: e.target.checked })}
        />
        <FullRow>
          <Features>
            {ALL_FEATURES.map((f) => (
              <CheckboxField
                key={f}
                label={FEATURE_LABELS[f]}
                checked={v.features.includes(f)}
                onChange={() => toggle(f)}
              />
            ))}
          </Features>
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

const PLATFORM_ACTIONS: Record<string, string> = {
  LOGIN_SUCCEEDED: "Login",
  LOGIN_FAILED: "Falha de login",
  LOGIN_BLOCKED: "Login bloqueado",
  LOGOUT: "Saída",
  TENANT_CREATED: "Clínica criada",
  TENANT_STATUS_CHANGED: "Situação da clínica alterada",
  SUBSCRIPTION_CHANGED: "Assinatura alterada",
  RECORD_UPDATED: "Alteração",
  PASSWORD_RESET_REQUESTED: "Pedido de redefinição de senha",
  PASSWORD_RESET_COMPLETED: "Senha redefinida",
  PASSWORD_CHANGED: "Senha alterada",
  SESSION_REVOKED: "Sessão encerrada",
  PLATFORM_ADMIN_BOOTSTRAPPED: "Administrador inicial criado",
};

function AuditTab() {
  const [action, setAction] = useState("");
  const events = useQuery({
    queryKey: ["platform-audit", action],
    queryFn: () => api.platform.audit(action || undefined),
  });
  return (
    <Stack $gap={4}>
      <div style={{ width: 260 }}>
        <SelectField
          label="Ação"
          value={action}
          onChange={(e) => setAction(e.target.value)}
          placeholder="Todas"
          options={Object.entries(PLATFORM_ACTIONS).map(([value, label]) => ({ value, label }))}
        />
      </div>
      <DataTable<PlatformEvent>
        caption="Auditoria da plataforma"
        rows={events.data}
        loading={events.isPending}
        rowKey={(e) => e.id}
        emptyTitle="Nenhum evento"
        columns={[
          { key: "when", header: "Quando", render: (e) => formatDateTime(e.occurredAt) },
          { key: "action", header: "Ação", render: (e) => PLATFORM_ACTIONS[e.action] ?? e.action },
          { key: "actor", header: "Usuário", render: (e) => e.actorEmail ?? "—" },
          { key: "resource", header: "Registro", hideOnMobile: true, render: (e) => e.resourceType ?? "—" },
          { key: "ip", header: "IP", hideOnMobile: true, render: (e) => e.ip ?? "—" },
        ]}
      />
    </Stack>
  );
}

const Features = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: ${({ theme }) => theme.space[2]};
`;
