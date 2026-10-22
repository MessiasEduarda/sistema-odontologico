"use client";

import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { PdfButton } from "@/components/domain/PdfButton";
import { PatientSelect } from "@/components/domain/PatientSelect";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Card, CardTitle, FilterBar, Muted, PageHeader, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, SelectField } from "@/components/ui/fields";
import { SearchInput } from "@/components/ui/SearchInput";
import { Tabs } from "@/components/ui/Tabs";
import { AlertsBox, AnamnesisForm, AnswersView } from "@/features/clinical/AnamnesisSection";
import { api } from "@/lib/api/endpoints";
import type { RecentAnamnesis } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { FORM_KINDS } from "@/lib/labels";
import { useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { FormsManager } from "./FormsManager";

const KIND_OPTIONS = Object.entries(FORM_KINDS).map(([value, k]) => ({ value, label: k.label }));

/** Página Anamnese: preencher fichas, acompanhar preenchimentos com alerta e editar as fichas da clínica. */
export function AnamnesisPage() {
  const can = useCan();
  const [tab, setTab] = useState<string | null>(null);
  const items = [
    can(P.CLINICAL_WRITE) && { id: "preencher", label: "Preencher ficha", content: <FillTab /> },
    { id: "preenchidas", label: "Preenchidas", content: <RecentTab /> },
    can(P.CLINIC_SETTINGS_MANAGE) && { id: "fichas", label: "Fichas da clínica", content: <FormsManager /> },
  ].filter(Boolean) as { id: string; label: string; content: React.ReactNode }[];
  return (
    <Stack>
      <PageHeader
        title="Anamnese"
        description="Histórico de saúde do paciente. Cada preenchimento gera uma nova versão; nada é apagado."
      />
      <Tabs label="Anamnese" active={tab ?? items[0]?.id ?? ""} onChange={setTab} items={items} />
    </Stack>
  );
}

function FillTab() {
  const [patient, setPatient] = useState<{ id: string; name: string } | null>(null);
  const [formId, setFormId] = useState("");
  const [filling, setFilling] = useState(false);
  const forms = useQuery({ queryKey: ["anamnesis-forms", false], queryFn: () => api.clinic.anamnesisForms() });
  const history = useQuery({
    queryKey: ["anamneses", patient?.id],
    queryFn: () => api.clinical.anamneses(patient?.id ?? ""),
    enabled: Boolean(patient),
  });
  const form = forms.data?.find((f) => f.id === formId) ?? forms.data?.[0];
  const latest = history.data?.find((a) => a.formId === form?.id);

  return (
    <Stack $gap={4}>
      <Card>
        <CardTitle>1. Escolha o paciente e a ficha</CardTitle>
        <FormGrid $columns={2}>
          <PatientSelect value={patient} onChange={setPatient} required />
          <SelectField
            label="Ficha"
            value={form?.id ?? ""}
            onChange={(e) => setFormId(e.target.value)}
            options={(forms.data ?? []).map((f) => ({
              value: f.id,
              label: `${f.name} (${FORM_KINDS[f.kind]?.label})`,
            }))}
          />
        </FormGrid>
      </Card>
      {patient && form && (
        <Card>
          <CardTitle>
            2. {form.name}
            <Row $gap={2}>
              {latest && <PdfButton load={() => ({ kind: "anamnesis", patientId: patient.id, data: latest })} />}
              <Button onClick={() => setFilling(true)}>{latest ? "Atualizar respostas" : "Preencher agora"}</Button>
            </Row>
          </CardTitle>
          {history.isPending ? (
            <Muted>Carregando histórico…</Muted>
          ) : latest ? (
            <Stack $gap={3}>
              <Muted>
                Última versão em {formatDateTime(latest.createdAt)} por {latest.createdByName ?? "—"}. As respostas vêm
                preenchidas para revisão.
              </Muted>
              {latest.alerts.length > 0 && <AlertsBox alerts={latest.alerts} />}
              <AnswersView anamnesis={latest} />
            </Stack>
          ) : (
            <Alert tone="warning">{patient.name} ainda não respondeu esta ficha.</Alert>
          )}
        </Card>
      )}
      {filling && patient && form && (
        <AnamnesisForm patientId={patient.id} form={form} previous={latest} onClose={() => setFilling(false)} />
      )}
    </Stack>
  );
}

function RecentTab() {
  const router = useRouter();
  const [kind, setKind] = useState("");
  const [alertsOnly, setAlertsOnly] = useState(false);
  const [q, setQ] = useState("");
  const list = useQuery({
    queryKey: ["recent-anamneses", kind, alertsOnly, q],
    queryFn: () => api.clinical.recentAnamneses({ kind, alertsOnly, q }),
  });
  return (
    <Stack $gap={4}>
      <FilterBar>
        <SearchInput label="Paciente" placeholder="Nome do paciente…" value={q} onChange={setQ} />
        <div style={{ width: 200 }}>
          <SelectField
            label="Tipo de ficha"
            placeholder="Todas"
            value={kind}
            onChange={(e) => setKind(e.target.value)}
            options={KIND_OPTIONS}
          />
        </div>
        <CheckboxField
          label="Somente com alertas"
          checked={alertsOnly}
          onChange={(e) => setAlertsOnly(e.target.checked)}
        />
      </FilterBar>
      <DataTable<RecentAnamnesis>
        caption="Anamneses preenchidas"
        rows={list.data}
        loading={list.isPending}
        error={list.error}
        onRetry={list.refetch}
        rowKey={(a) => a.id}
        onRowClick={(a) => router.push(`/pacientes/${a.patientId}?aba=prontuario`)}
        emptyTitle="Nenhuma anamnese encontrada"
        columns={[
          { key: "date", header: "Data", render: (a) => formatDateTime(a.createdAt) },
          { key: "patient", header: "Paciente", render: (a) => <strong>{a.patientName}</strong> },
          {
            key: "form",
            header: "Ficha",
            render: (a) => <Badge $tone={FORM_KINDS[a.formKind]?.tone}>{a.formName}</Badge>,
          },
          {
            key: "alerts",
            header: "Alertas",
            render: (a) =>
              a.alerts.length > 0 ? (
                <span title={a.alerts.map((x) => x.text).join("\n")}>
                  <Badge $tone="danger">{a.alerts.length} alerta(s)</Badge>
                </span>
              ) : (
                <Badge $tone="success">Sem alertas</Badge>
              ),
          },
          {
            key: "by",
            header: "Preenchida por",
            hideOnMobile: true,
            render: (a) => `${a.filledBy === "PATIENT" ? "Paciente" : "Profissional"} · ${a.createdByName ?? "—"}`,
          },
          {
            key: "pdf",
            header: "",
            render: (a) => (
              <PdfButton
                compact
                load={async () => {
                  const all = await api.clinical.anamneses(a.patientId);
                  const data = all.find((x) => x.id === a.id);
                  if (!data) throw new Error("Anamnese não encontrada.");
                  return { kind: "anamnesis", patientId: a.patientId, data };
                }}
              />
            ),
          },
        ]}
      />
    </Stack>
  );
}
