"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Badge, Card, CardTitle, DetailList, Muted, PageHeader, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow, SelectField } from "@/components/ui/fields";
import { LoadingState } from "@/components/ui/LoadingState";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { ClinicSettings } from "@/lib/api/types";
import { formatDate, formatMoney } from "@/lib/format";
import { FEATURE_LABELS, SUBSCRIPTION_STATUS, UF_OPTIONS } from "@/lib/labels";
import { useAction } from "@/lib/mutations";
import { useRequiredFields } from "@/lib/useRequiredFields";

export function SettingsPage() {
  const settings = useQuery({ queryKey: ["clinic-settings"], queryFn: api.clinic.settings });
  return (
    <Stack>
      <PageHeader title="Configurações" />
      {settings.isPending ? <LoadingState /> : settings.data && <SettingsForm initial={settings.data} />}
      <SubscriptionCard />
    </Stack>
  );
}

function SettingsForm({ initial }: { initial: ClinicSettings }) {
  const [v, setV] = useState(initial);
  const set = <K extends keyof ClinicSettings>(k: K, value: ClinicSettings[K]) => setV((s) => ({ ...s, [k]: value }));
  const text = (k: keyof ClinicSettings) => ({
    value: (v[k] as string | null) ?? "",
    onChange: (e: { target: { value: string } }) => set(k, (e.target.value || null) as never),
  });
  const form = useRequiredFields({ name: !v.name.trim() && "Informe o nome de exibição da clínica." });
  const save = useAction(
    () => {
      const { timeZone: _tz, logoUpdatedAt: _logo, ...body } = v;
      void _tz;
      void _logo;
      return api.clinic.saveSettings(body);
    },
    {
      success: "Configurações salvas com sucesso!",
      invalidate: [["clinic-settings"], ["auth", "me"], ["anamnesis-forms"]],
    },
  );
  return (
    <Card>
      <CardTitle>Dados da clínica</CardTitle>
      <FormGrid $columns={3}>
        <TextField
          label="Nome de exibição"
          required
          error={form.errors.name}
          maxLength={160}
          value={v.name}
          onChange={(e) => set("name", e.target.value)}
        />
        <TextField label="Razão social" maxLength={160} {...text("legalName")} />
        <TextField label="CNPJ / CPF" maxLength={18} {...text("document")} />
        <TextField label="Telefone" maxLength={20} {...text("phone")} />
        <TextField label="E-mail" type="email" maxLength={254} {...text("email")} />
        <TextField label="CEP" maxLength={9} {...text("zipCode")} />
        <FullRow>
          <TextField label="Endereço" maxLength={200} {...text("address")} />
        </FullRow>
        <TextField label="Cidade" maxLength={100} {...text("city")} />
        <SelectField
          label="UF"
          placeholder="—"
          value={v.state ?? ""}
          onChange={(e) => set("state", e.target.value || null)}
          options={UF_OPTIONS}
        />
        <TextField label="Fuso horário" value={v.timeZone} disabled hint="Definido pela plataforma." />
      </FormGrid>
      <CardTitle style={{ marginTop: 24 }}>Agenda e lembretes</CardTitle>
      <FormGrid $columns={3}>
        <TextField
          label="Duração padrão do atendimento (min)"
          type="number"
          min={5}
          max={480}
          step={5}
          value={v.defaultAppointmentMinutes}
          onChange={(e) => set("defaultAppointmentMinutes", Number(e.target.value))}
        />
        <TextField
          label="Enviar lembrete com antecedência de (horas)"
          type="number"
          min={1}
          max={168}
          value={v.reminderHoursBefore}
          onChange={(e) => set("reminderHoursBefore", Number(e.target.value))}
        />
        <CheckboxField
          label="Enviar lembretes automáticos por e-mail"
          checked={v.remindersEnabled}
          onChange={(e) => set("remindersEnabled", e.target.checked)}
        />
      </FormGrid>
      <CardTitle style={{ marginTop: 24 }}>Serviços oferecidos</CardTitle>
      <Stack $gap={2}>
        <CheckboxField
          label="A clínica realiza harmonização orofacial (toxina botulínica, preenchimentos, bioestimuladores…)"
          checked={v.aestheticsEnabled}
          onChange={(e) => set("aestheticsEnabled", e.target.checked)}
        />
        <Muted>
          Ativa o menu Harmonização (registro das aplicações com produto, lote e validade), a ficha de anamnese de
          harmonização e a aba Harmonização na ficha do paciente.
        </Muted>
      </Stack>
      {save.error && <Alert tone="danger">{save.error.message}</Alert>}
      <Row $justify="flex-end" style={{ marginTop: 16 }}>
        <Button onClick={() => form.check() && save.mutate(undefined)} loading={save.isPending}>
          Salvar configurações
        </Button>
      </Row>
    </Card>
  );
}

function SubscriptionCard() {
  const sub = useQuery({ queryKey: ["clinic-subscription"], queryFn: api.clinic.subscription, retry: false });
  if (!sub.data) return null;
  const s = sub.data;
  return (
    <Card>
      <CardTitle>
        Plano contratado{" "}
        <Badge $tone={SUBSCRIPTION_STATUS[s.status]?.tone}>{SUBSCRIPTION_STATUS[s.status]?.label}</Badge>
      </CardTitle>
      {s.billing.state !== "OK" && (
        <Alert tone="danger">
          Pagamento da assinatura vencido em {formatDate(s.billing.dueDate)}.{" "}
          {s.paymentUrl ? (
            <a href={s.paymentUrl} target="_blank" rel="noopener noreferrer">
              Pagar agora
            </a>
          ) : (
            "Entre em contato com o suporte da plataforma."
          )}
        </Alert>
      )}
      <DetailList>
        <div>
          <dt>Plano</dt>
          <dd>
            {s.planName} · {formatMoney(s.priceMonthly)}/mês
          </dd>
        </div>
        <div>
          <dt>{s.status === "TRIALING" ? "Avaliação até" : "Período atual até"}</dt>
          <dd>{formatDate(s.status === "TRIALING" ? s.trialEndsAt : s.currentPeriodEnd)}</dd>
        </div>
        <div>
          <dt>Profissionais ativos</dt>
          <dd>
            {s.professionals} {s.maxProfessionals ? `de ${s.maxProfessionals}` : "(ilimitado)"}
          </dd>
        </div>
        <div>
          <dt>Pacientes ativos</dt>
          <dd>
            {s.patients} {s.maxPatients ? `de ${s.maxPatients}` : "(ilimitado)"}
          </dd>
        </div>
        <div>
          <dt>Recursos incluídos</dt>
          <dd>
            <Row $gap={1}>
              {s.features.map((f) => (
                <Badge key={f} $tone="primary">
                  {FEATURE_LABELS[f] ?? f}
                </Badge>
              ))}
            </Row>
          </dd>
        </div>
      </DetailList>
    </Card>
  );
}
