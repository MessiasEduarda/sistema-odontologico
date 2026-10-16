"use client";

import { useQuery } from "@tanstack/react-query";
import { Plus, Printer } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { PdfButton } from "@/components/domain/PdfButton";
import { PatientSelect } from "@/components/domain/PatientSelect";
import { PrintSheet, SignatureLine } from "@/components/domain/PrintSheet";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Card, PageHeader, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow, SelectField, TextAreaField } from "@/components/ui/fields";
import { Modal } from "@/components/ui/Modal";
import { Tabs } from "@/components/ui/Tabs";
import { TextField } from "@/components/ui/TextField";
import { useMe } from "@/features/auth/hooks";
import { api } from "@/lib/api/endpoints";
import type { CareTemplate, CareTemplateInput, ClinicalDocument, IssuedDocument } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { CARE_CATEGORIES, CARE_MOMENTS } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { printSheet } from "@/lib/print";

const CATEGORY_OPTIONS = Object.entries(CARE_CATEGORIES).map(([value, label]) => ({ value, label }));
const MOMENT_OPTIONS = Object.entries(CARE_MOMENTS).map(([value, label]) => ({ value, label }));

/**
 * Orientações e cuidados ao paciente (antes e depois dos procedimentos). A clínica escreve os próprios modelos; ao
 * entregar, o texto vira documento do prontuário, pode ser impresso e enviado por e-mail.
 */
export function CarePage() {
  const can = useCan();
  const [tab, setTab] = useState<string | null>(null);
  const items = [
    can(P.CLINICAL_WRITE) && { id: "entregar", label: "Entregar ao paciente", content: <DeliverTab /> },
    { id: "entregues", label: "Entregues", content: <DeliveredTab /> },
    can(P.CLINICAL_WRITE, P.CLINIC_SETTINGS_MANAGE) && { id: "modelos", label: "Modelos", content: <TemplatesTab /> },
  ].filter(Boolean) as { id: string; label: string; content: React.ReactNode }[];
  return (
    <Stack>
      <PageHeader
        title="Orientações e cuidados"
        description="Recomendações antes e depois dos procedimentos, entregues por escrito, impressas ou por e-mail."
      />
      <Tabs label="Orientações" active={tab ?? items[0]?.id ?? ""} onChange={setTab} items={items} />
    </Stack>
  );
}

function DeliverTab() {
  const templates = useQuery({ queryKey: ["care-templates", false], queryFn: () => api.clinic.careTemplates() });
  const professionals = useQuery({ queryKey: ["professionals", true], queryFn: () => api.clinic.professionals(true) });
  const [patient, setPatient] = useState<{ id: string; name: string } | null>(null);
  const [templateId, setTemplateId] = useState("");
  const [professionalId, setProfessionalId] = useState("");
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [sendEmail, setSendEmail] = useState(true);
  const [issued, setIssued] = useState<{ doc: ClinicalDocument; patient: string; patientId: string } | null>(null);
  const issue = useAction(
    () => api.clinical.issueCare(patient?.id ?? "", { professionalId, title, content, sendEmail }),
    {
      success: sendEmail
        ? "Orientação registrada e enviada com sucesso! (o e-mail só sai se o paciente permitir)"
        : "Orientação registrada com sucesso!",
      invalidate: [["documents"], ["issued-care"]],
      onSuccess: (doc) => {
        setIssued({ doc, patient: patient?.name ?? "", patientId: patient?.id ?? "" });
        setPatient(null);
        setTemplateId("");
        setTitle("");
        setContent("");
      },
    },
  );
  const pick = (id: string) => {
    setTemplateId(id);
    const t = templates.data?.find((x) => x.id === id);
    if (t) {
      setTitle(t.title);
      setContent(t.body);
    }
  };

  return (
    <Stack $gap={4}>
      {templates.data && templates.data.length === 0 && (
        <Alert tone="info">
          Ainda não há modelos. Cadastre na aba Modelos as orientações definidas pelo responsável técnico para usar com
          um clique, ou escreva o texto diretamente abaixo.
        </Alert>
      )}
      <Card>
        <FormGrid $columns={2}>
          <PatientSelect value={patient} onChange={setPatient} required />
          <SelectField
            label="Profissional responsável"
            required
            placeholder="Selecione…"
            value={professionalId}
            onChange={(e) => setProfessionalId(e.target.value)}
            options={(professionals.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
          />
          <FullRow>
            <SelectField
              label="Modelo"
              placeholder="(escrever sem modelo)"
              value={templateId}
              onChange={(e) => pick(e.target.value)}
              options={(templates.data ?? []).map((t) => ({
                value: t.id,
                label: `${CARE_CATEGORIES[t.category]} · ${CARE_MOMENTS[t.moment]} · ${t.title}`,
              }))}
            />
          </FullRow>
          <FullRow>
            <TextField
              label="Título"
              required
              maxLength={160}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </FullRow>
          <FullRow>
            <TextAreaField
              label="Orientações"
              required
              rows={12}
              maxLength={20000}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              hint="Você pode ajustar o texto para este paciente antes de entregar."
            />
          </FullRow>
          <CheckboxField
            label="Enviar também por e-mail ao paciente"
            checked={sendEmail}
            onChange={(e) => setSendEmail(e.target.checked)}
          />
        </FormGrid>
        {issue.error && <Alert tone="danger">{issue.error.message}</Alert>}
        <Row $justify="flex-end" style={{ marginTop: 16 }}>
          <Button
            onClick={() => issue.mutate(undefined)}
            loading={issue.isPending}
            disabled={!patient || !professionalId || !title.trim() || !content.trim()}
          >
            Registrar e entregar
          </Button>
        </Row>
      </Card>
      {issued && (
        <PrintDialog
          doc={issued.doc}
          patientId={issued.patientId}
          patientName={issued.patient}
          onClose={() => setIssued(null)}
        />
      )}
    </Stack>
  );
}

function PrintDialog({
  doc,
  patientId,
  patientName,
  onClose,
}: {
  doc: ClinicalDocument;
  patientId: string;
  patientName: string;
  onClose: () => void;
}) {
  const { data: me } = useMe();
  return (
    <Modal
      open
      onClose={onClose}
      title="Orientação registrada"
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Fechar
          </Button>
          <Button variant="outline" onClick={printSheet}>
            <Printer size={16} aria-hidden /> Imprimir
          </Button>
          <PdfButton variant="primary" load={() => ({ kind: "document", patientId, data: doc })} />
        </>
      }
    >
      <PrintSheet
        clinicName={me?.activeClinic?.name ?? ""}
        title={doc.title}
        footer={
          <>
            <p>
              Paciente: {patientName} · Entregue em {formatDateTime(doc.issuedAt)}
            </p>
            <SignatureLine>
              {doc.professionalName}
              {doc.professionalCro ? ` · ${doc.professionalCro}` : ""}
            </SignatureLine>
          </>
        }
      >
        {doc.content}
      </PrintSheet>
    </Modal>
  );
}

function DeliveredTab() {
  const router = useRouter();
  const list = useQuery({ queryKey: ["issued-care"], queryFn: () => api.clinical.recentDocuments("INSTRUCTIONS") });
  return (
    <DataTable<IssuedDocument>
      caption="Orientações entregues"
      rows={list.data}
      loading={list.isPending}
      error={list.error}
      onRetry={list.refetch}
      rowKey={(d) => d.id}
      onRowClick={(d) => router.push(`/pacientes/${d.patientId}?aba=prontuario&secao=documentos`)}
      emptyTitle="Nenhuma orientação entregue"
      columns={[
        { key: "date", header: "Data", render: (d) => formatDateTime(d.issuedAt) },
        { key: "patient", header: "Paciente", render: (d) => <strong>{d.patientName}</strong> },
        { key: "title", header: "Orientação", render: (d) => d.title },
        { key: "prof", header: "Profissional", hideOnMobile: true, render: (d) => d.professionalName },
        {
          key: "pdf",
          header: "",
          render: (d) => (
            <PdfButton
              compact
              load={async () => {
                const data = (await api.clinical.documents(d.patientId)).find((x) => x.id === d.id);
                if (!data) throw new Error("Documento não encontrado.");
                return { kind: "document", patientId: d.patientId, data };
              }}
            />
          ),
        },
      ]}
    />
  );
}

function TemplatesTab() {
  const [editing, setEditing] = useState<CareTemplate | "new" | null>(null);
  const list = useQuery({ queryKey: ["care-templates", true], queryFn: () => api.clinic.careTemplates(true) });
  return (
    <Stack $gap={4}>
      <Alert tone="warning">
        Escreva aqui as orientações definidas pelo responsável técnico da clínica (por procedimento, antes e depois). O
        sistema não traz textos clínicos prontos.
      </Alert>
      <Row $justify="flex-end">
        <Button onClick={() => setEditing("new")}>
          <Plus size={16} aria-hidden /> Novo modelo
        </Button>
      </Row>
      <DataTable<CareTemplate>
        caption="Modelos de orientação"
        rows={list.data}
        loading={list.isPending}
        rowKey={(t) => t.id}
        onRowClick={setEditing}
        emptyTitle="Nenhum modelo"
        columns={[
          { key: "title", header: "Título", render: (t) => <strong>{t.title}</strong> },
          { key: "category", header: "Área", render: (t) => CARE_CATEGORIES[t.category] },
          { key: "moment", header: "Momento", render: (t) => CARE_MOMENTS[t.moment] },
          {
            key: "status",
            header: "Situação",
            render: (t) => (t.active ? <Badge $tone="success">Ativo</Badge> : <Badge>Inativo</Badge>),
          },
        ]}
      />
      {editing && <TemplateForm template={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </Stack>
  );
}

function TemplateForm({ template, onClose }: { template: CareTemplate | null; onClose: () => void }) {
  const [v, setV] = useState<CareTemplateInput>({
    title: template?.title ?? "",
    category: template?.category ?? "DENTAL",
    moment: template?.moment ?? "AFTER",
    body: template?.body ?? "",
    active: template?.active ?? true,
  });
  const save = useAction(
    () => (template ? api.clinic.updateCareTemplate(template.id, v) : api.clinic.createCareTemplate(v)),
    { success: "Modelo salvo com sucesso!", invalidate: [["care-templates"]], onSuccess: onClose },
  );
  return (
    <Modal
      open
      onClose={onClose}
      title={template ? "Editar modelo de orientação" : "Novo modelo de orientação"}
      size="lg"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => save.mutate(undefined)}
            loading={save.isPending}
            disabled={!v.title.trim() || !v.body.trim()}
          >
            Salvar
          </Button>
        </>
      }
    >
      <FormGrid $columns={2}>
        <FullRow>
          <TextField
            label="Título"
            required
            maxLength={160}
            value={v.title}
            onChange={(e) => setV({ ...v, title: e.target.value })}
          />
        </FullRow>
        <SelectField
          label="Área"
          value={v.category}
          onChange={(e) => setV({ ...v, category: e.target.value as CareTemplateInput["category"] })}
          options={CATEGORY_OPTIONS}
        />
        <SelectField
          label="Momento"
          value={v.moment}
          onChange={(e) => setV({ ...v, moment: e.target.value as CareTemplateInput["moment"] })}
          options={MOMENT_OPTIONS}
        />
        <FullRow>
          <TextAreaField
            label="Texto das orientações"
            required
            rows={14}
            maxLength={20000}
            value={v.body}
            onChange={(e) => setV({ ...v, body: e.target.value })}
          />
        </FullRow>
        <CheckboxField
          label="Ativo (aparece para entrega)"
          checked={v.active}
          onChange={(e) => setV({ ...v, active: e.target.checked })}
        />
        {save.error && (
          <FullRow>
            <Alert tone="danger">{save.error.message}</Alert>
          </FullRow>
        )}
      </FormGrid>
    </Modal>
  );
}
