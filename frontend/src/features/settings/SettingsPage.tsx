"use client";

import { useQuery } from "@tanstack/react-query";
import { ImageUp } from "lucide-react";
import { useRef, useState } from "react";
import styled from "styled-components";
import { ClinicLogo } from "@/components/domain/ClinicLogo";
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

export function SettingsPage() {
  const settings = useQuery({ queryKey: ["clinic-settings"], queryFn: api.clinic.settings });
  return (
    <Stack>
      <PageHeader title="Configurações" />
      {settings.data && <LogoCard name={settings.data.name} logoVersion={settings.data.logoUpdatedAt} />}
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
  const save = useAction(
    () => {
      const { timeZone: _tz, logoUpdatedAt: _logo, ...body } = v;
      void _tz;
      void _logo;
      return api.clinic.saveSettings(body);
    },
    { success: "Configurações salvas.", invalidate: [["clinic-settings"], ["auth", "me"], ["anamnesis-forms"]] },
  );
  return (
    <Card>
      <CardTitle>Dados da clínica</CardTitle>
      <FormGrid $columns={3}>
        <TextField
          label="Nome de exibição"
          required
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
        <Button onClick={() => save.mutate(undefined)} loading={save.isPending} disabled={!v.name.trim()}>
          Salvar configurações
        </Button>
      </Row>
    </Card>
  );
}

const MAX_LOGO_BYTES = 1024 * 1024;

/** Logo exibida no menu lateral e nos PDFs. Sempre no mesmo quadro, sem cortar nem distorcer a imagem. */
function LogoCard({ name, logoVersion }: { name: string; logoVersion: string | null }) {
  const input = useRef<HTMLInputElement>(null);
  const [localError, setLocalError] = useState<string>();
  const upload = useAction((file: File) => api.clinic.uploadLogo(file), {
    success: "Logo atualizada.",
    invalidate: [["clinic-settings"]],
    onSuccess: () => {
      if (input.current) input.current.value = "";
    },
  });
  const remove = useAction(() => api.clinic.deleteLogo(), {
    success: "Logo removida.",
    invalidate: [["clinic-settings"]],
  });
  const pick = (file: File | undefined) => {
    setLocalError(undefined);
    if (!file) return;
    if (file.type !== "image/png") {
      setLocalError("Envie a logo em PNG.");
      return;
    }
    if (file.size > MAX_LOGO_BYTES) {
      setLocalError("A imagem deve ter no máximo 1 MB.");
      return;
    }
    upload.mutate(file);
  };
  return (
    <Card>
      <CardTitle>Logo da clínica</CardTitle>
      <LogoRow>
        <ClinicLogo name={name} logoVersion={logoVersion} size={96} tone="light" />
        <Stack $gap={3}>
          <Muted>
            Aparece no topo do menu e no cabeçalho dos PDFs, sempre no mesmo tamanho e ocupando o quadro inteiro, sobre
            a cor do menu. Envie em PNG, de até 1 MB, de preferência quadrada (imagens retangulares têm as bordas
            ajustadas ao quadro). Sem logo, o sistema mostra as iniciais da clínica.
          </Muted>
          <Row $gap={2}>
            <input ref={input} type="file" accept="image/png" hidden onChange={(e) => pick(e.target.files?.[0])} />
            <Button onClick={() => input.current?.click()} loading={upload.isPending}>
              <ImageUp size={16} aria-hidden /> {logoVersion ? "Trocar logo" : "Enviar logo"}
            </Button>
            {logoVersion && (
              <Button variant="outline" onClick={() => remove.mutate(undefined)} loading={remove.isPending}>
                Remover
              </Button>
            )}
          </Row>
          {localError && <Alert tone="danger">{localError}</Alert>}
        </Stack>
      </LogoRow>
    </Card>
  );
}

const LogoRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${({ theme }) => theme.space[6]};

  > div:last-child {
    flex: 1;
    min-width: 260px;
  }
`;

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
      {s.status === "PAST_DUE" && (
        <Alert tone="danger">Há pendência na assinatura. Entre em contato com o suporte da plataforma.</Alert>
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
