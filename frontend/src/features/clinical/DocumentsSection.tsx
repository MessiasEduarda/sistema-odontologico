"use client";

import { useQuery } from "@tanstack/react-query";
import { Ban, Pencil, Printer } from "lucide-react";
import { useState } from "react";
import { PdfButton } from "@/components/domain/PdfButton";
import { ReasonDialog } from "@/components/domain/ReasonDialog";
import { PrintSheet, SignatureLine } from "@/components/domain/PrintSheet";
import { Alert } from "@/components/ui/Alert";
import { BlankTextField } from "@/components/ui/BlankTextField";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Muted, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow, SelectField } from "@/components/ui/fields";
import { IconButton } from "@/components/ui/IconButton";
import { Modal } from "@/components/ui/Modal";
import { SearchInput, useRowSearch } from "@/components/ui/SearchInput";
import { TextField } from "@/components/ui/TextField";
import { useMe } from "@/features/auth/hooks";
import { api } from "@/lib/api/endpoints";
import type { ClinicalDocument } from "@/lib/api/types";
import { formatDate, formatDateTime } from "@/lib/format";
import { DOCUMENT_TYPES } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { printSheet } from "@/lib/print";
import { useRequiredFields } from "@/lib/useRequiredFields";

const STARTERS: Record<string, { title: string; content: (patient: string, date: string) => string }> = {
  PRESCRIPTION: {
    title: "Receituário",
    content: (patient) =>
      `Paciente: ${patient}\n\nUso ______:\n\n1. ____________________ ........ ____\n   ____________________________________\n`,
  },
  CERTIFICATE: {
    title: "Atestado",
    content: (patient, date) =>
      `Atesto, para os devidos fins, que ${patient} esteve em atendimento odontológico nesta clínica em ${date}, ` +
      `das ___h___ às ___h___, devendo permanecer afastado(a) de suas atividades por ___ dia(s).\n\n` +
      `(Inclua CID somente com autorização expressa do paciente.)`,
  },
  REFERRAL: {
    title: "Encaminhamento",
    content: (patient) =>
      `Encaminho o(a) paciente ${patient} para avaliação de ____________________.\n\nMotivo: ____________________\n`,
  },
  OTHER: { title: "Declaração", content: (patient) => `Paciente: ${patient}\n\n` },
};

export function DocumentsSection({ patientId, patientName }: { patientId: string; patientName: string }) {
  const can = useCan();
  const [creating, setCreating] = useState(false);
  const [printing, setPrinting] = useState<ClinicalDocument | null>(null);
  const docs = useQuery({ queryKey: ["documents", patientId], queryFn: () => api.clinical.documents(patientId) });
  const search = useRowSearch(docs.data, (d) => [d.professionalName, d.title]);
  const [editing, setEditing] = useState<ClinicalDocument | null>(null);
  const [voiding, setVoiding] = useState<ClinicalDocument | null>(null);
  const writable = can(P.CLINICAL_WRITE);
  const share = useAction(
    (v: { id: string; shared: boolean }) => api.clinical.setDocumentSharing(patientId, v.id, v.shared),
    {
      success: (d) =>
        d.sharedWithPatient
          ? "Documento liberado no portal do paciente."
          : "Documento escondido do portal do paciente.",
      invalidate: [["documents", patientId]],
    },
  );
  const voidIt = useAction((reason: string) => api.clinical.voidDocument(patientId, voiding?.id ?? "", reason), {
    success: "Documento anulado com sucesso!",
    invalidate: [["documents", patientId], ["issued-care"]],
    onSuccess: () => setVoiding(null),
  });

  return (
    <Stack $gap={4}>
      <Row $justify="space-between">
        <Muted>
          Receituários, atestados e encaminhamentos emitidos. Correções e anulações ficam registradas no histórico do
          prontuário.
        </Muted>
        {writable && <Button onClick={() => setCreating(true)}>Emitir documento</Button>}
      </Row>
      <SearchInput
        label="Buscar por nome"
        placeholder="Profissional ou título…"
        value={search.query}
        onChange={search.setQuery}
      />
      <DataTable<ClinicalDocument>
        caption="Documentos emitidos"
        rows={search.rows}
        loading={docs.isPending}
        error={docs.error}
        onRetry={() => docs.refetch()}
        rowKey={(d) => d.id}
        emptyTitle="Nenhum documento emitido"
        onRowClick={setPrinting}
        columns={[
          { key: "date", header: "Emissão", render: (d) => formatDateTime(d.issuedAt) },
          {
            key: "type",
            header: "Tipo",
            render: (d) => <Badge $tone="primary">{DOCUMENT_TYPES.find((t) => t.value === d.type)?.label}</Badge>,
          },
          {
            key: "title",
            header: "Título",
            render: (d) =>
              d.voidedAt ? (
                <span>
                  <s>{d.title}</s> <Badge $tone="danger">Anulado</Badge>
                  <br />
                  <Muted>{d.voidReason}</Muted>
                </span>
              ) : (
                <span>
                  {d.title}
                  {d.updatedAt && <Muted> · corrigido em {formatDateTime(d.updatedAt)}</Muted>}
                </span>
              ),
          },
          { key: "prof", header: "Profissional", hideOnMobile: true, render: (d) => d.professionalName },
          {
            key: "portal",
            header: "Portal",
            render: (d) =>
              d.voidedAt ? (
                "—"
              ) : writable ? (
                <span onClick={(e) => e.stopPropagation()}>
                  <CheckboxField
                    label="Visível ao paciente"
                    checked={d.sharedWithPatient}
                    disabled={share.isPending && share.variables?.id === d.id}
                    onChange={(e) => share.mutate({ id: d.id, shared: e.target.checked })}
                  />
                </span>
              ) : d.sharedWithPatient ? (
                <Badge $tone="success">Visível</Badge>
              ) : (
                <Muted>Não</Muted>
              ),
          },
          {
            key: "print",
            header: "",
            render: (d) => (
              <Row $gap={2} $wrap={false}>
                <PdfButton compact load={() => ({ kind: "document", patientId, data: d })} />
                <IconButton
                  icon={Printer}
                  label="Visualizar e imprimir"
                  onClick={(e) => {
                    e.stopPropagation();
                    setPrinting(d);
                  }}
                />
                {writable && !d.voidedAt && (
                  <>
                    <IconButton
                      icon={Pencil}
                      label="Corrigir documento"
                      onClick={(e) => {
                        e.stopPropagation();
                        setEditing(d);
                      }}
                    />
                    <IconButton
                      icon={Ban}
                      label="Anular documento"
                      tone="danger"
                      onClick={(e) => {
                        e.stopPropagation();
                        setVoiding(d);
                      }}
                    />
                  </>
                )}
              </Row>
            ),
          },
        ]}
      />
      {creating && <DocumentForm patientId={patientId} patientName={patientName} onClose={() => setCreating(false)} />}
      {editing && <DocumentEditForm patientId={patientId} doc={editing} onClose={() => setEditing(null)} />}
      <ReasonDialog
        open={Boolean(voiding)}
        title="Anular documento"
        description="O documento continua no histórico do prontuário, marcado como anulado e com o motivo, e sai do portal do paciente."
        confirmLabel="Anular"
        danger
        loading={voidIt.isPending}
        onConfirm={(reason) => voidIt.mutate(reason)}
        onClose={() => setVoiding(null)}
      />
      <PrintDocument doc={printing} patientId={patientId} patientName={patientName} onClose={() => setPrinting(null)} />
    </Stack>
  );
}

function DocumentForm({
  patientId,
  patientName,
  onClose,
}: {
  patientId: string;
  patientName: string;
  onClose: () => void;
}) {
  const professionals = useQuery({ queryKey: ["professionals", true], queryFn: () => api.clinic.professionals(true) });
  const today = formatDate(new Date().toISOString());
  const [type, setType] = useState("PRESCRIPTION");
  const [title, setTitle] = useState(STARTERS.PRESCRIPTION?.title ?? "");
  const [content, setContent] = useState(STARTERS.PRESCRIPTION?.content(patientName, today) ?? "");
  const [professionalId, setProfessionalId] = useState("");
  const [sharedWithPatient, setSharedWithPatient] = useState(false);
  const form = useRequiredFields({
    professionalId: !professionalId && "Selecione o profissional responsável.",
    title: !title.trim() && "Informe o título.",
    content: !content.trim() && "Escreva o conteúdo do documento.",
  });
  const save = useAction(
    () => api.clinical.addDocument(patientId, { professionalId, type, title, content, sharedWithPatient }),
    {
      success: "Documento emitido com sucesso!",
      invalidate: [["documents", patientId]],
      onSuccess: onClose,
    },
  );

  const changeType = (t: string) => {
    setType(t);
    setTitle(STARTERS[t]?.title ?? "");
    setContent(STARTERS[t]?.content(patientName, today) ?? "");
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="Emitir documento"
      size="lg"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => form.check() && save.mutate(undefined)} loading={save.isPending}>
            Emitir
          </Button>
        </>
      }
    >
      <FormGrid $columns={2}>
        <SelectField label="Tipo" value={type} onChange={(e) => changeType(e.target.value)} options={DOCUMENT_TYPES} />
        <SelectField
          label="Profissional responsável"
          required
          error={form.errors.professionalId}
          placeholder="Selecione…"
          value={professionalId}
          onChange={(e) => setProfessionalId(e.target.value)}
          options={(professionals.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
        />
        <FullRow>
          <TextField
            label="Título"
            required
            maxLength={160}
            error={form.errors.title}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </FullRow>
        <FullRow>
          <BlankTextField
            label="Conteúdo"
            rows={12}
            maxLength={20000}
            error={form.errors.content}
            value={content}
            onChange={setContent}
          />
        </FullRow>
        <FullRow>
          <CheckboxField
            label="Disponibilizar no portal do paciente"
            checked={sharedWithPatient}
            onChange={(e) => setSharedWithPatient(e.target.checked)}
          />
        </FullRow>
        <FullRow>
          <Alert tone="info">
            O conteúdo clínico (medicamentos, posologia, afastamento) é de responsabilidade do profissional.
          </Alert>
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

function DocumentEditForm({
  patientId,
  doc,
  onClose,
}: {
  patientId: string;
  doc: ClinicalDocument;
  onClose: () => void;
}) {
  const [title, setTitle] = useState(doc.title);
  const [content, setContent] = useState(doc.content);
  const form = useRequiredFields({
    title: !title.trim() && "Informe o título.",
    content: !content.trim() && "Escreva o conteúdo do documento.",
  });
  const save = useAction(() => api.clinical.updateDocument(patientId, doc.id, { title, content }), {
    success: "Documento corrigido com sucesso!",
    invalidate: [["documents", patientId], ["issued-care"]],
    onSuccess: onClose,
  });
  return (
    <Modal
      open
      onClose={onClose}
      title="Corrigir documento"
      size="lg"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => form.check() && save.mutate(undefined)} loading={save.isPending}>
            Salvar correção
          </Button>
        </>
      }
    >
      <FormGrid $columns={1}>
        <TextField
          label="Título"
          required
          maxLength={160}
          error={form.errors.title}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
        <BlankTextField
          label="Conteúdo"
          rows={12}
          maxLength={20000}
          error={form.errors.content}
          value={content}
          onChange={setContent}
        />
        <Alert tone="info">
          A versão anterior fica guardada no histórico do prontuário. Para trocar o tipo ou o profissional, anule este
          documento e emita um novo.
        </Alert>
        {save.error && <Alert tone="danger">{save.error.message}</Alert>}
      </FormGrid>
    </Modal>
  );
}

function PrintDocument({
  doc,
  patientId,
  patientName,
  onClose,
}: {
  doc: ClinicalDocument | null;
  patientId: string;
  patientName: string;
  onClose: () => void;
}) {
  const { data: me } = useMe();
  const settings = useQuery({ queryKey: ["clinic-settings"], queryFn: api.clinic.settings, enabled: Boolean(doc) });
  const info = [
    settings.data?.address,
    settings.data?.city && `${settings.data.city}/${settings.data.state ?? ""}`,
    settings.data?.phone,
  ]
    .filter(Boolean)
    .join(" · ");
  return (
    <Modal
      open={Boolean(doc)}
      onClose={onClose}
      title={doc?.title ?? "Documento"}
      size="lg"
      footer={
        doc && (
          <>
            <Button variant="outline" onClick={printSheet}>
              <Printer size={16} aria-hidden /> Imprimir
            </Button>
            <PdfButton variant="primary" load={() => ({ kind: "document", patientId, data: doc })} />
          </>
        )
      }
    >
      {doc && (
        <PrintSheet
          clinicName={settings.data?.name ?? me?.activeClinic?.name ?? ""}
          clinicInfo={info}
          title={doc.title}
          footer={
            <>
              <p>Emitido em {formatDate(doc.issuedAt)}.</p>
              <SignatureLine>
                {doc.professionalName}
                <br />
                {doc.professionalCro}
              </SignatureLine>
            </>
          }
        >
          {doc.content.includes(patientName) ? doc.content : `Paciente: ${patientName}\n\n${doc.content}`}
        </PrintSheet>
      )}
    </Modal>
  );
}
