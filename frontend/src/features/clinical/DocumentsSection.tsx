"use client";

import { useQuery } from "@tanstack/react-query";
import { Printer } from "lucide-react";
import { useState } from "react";
import { PdfButton } from "@/components/domain/PdfButton";
import { PrintSheet, SignatureLine } from "@/components/domain/PrintSheet";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Muted, Row, Stack } from "@/components/ui/display";
import { FormGrid, FullRow, SelectField, TextAreaField } from "@/components/ui/fields";
import { IconButton } from "@/components/ui/IconButton";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { useMe } from "@/features/auth/hooks";
import { api } from "@/lib/api/endpoints";
import type { ClinicalDocument } from "@/lib/api/types";
import { formatDate, formatDateTime } from "@/lib/format";
import { DOCUMENT_TYPES } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { printSheet } from "@/lib/print";

/** Modelos de texto inicial (o profissional completa e é responsável pelo conteúdo clínico). */
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

  return (
    <Stack $gap={4}>
      <Row $justify="space-between">
        <Muted>Receituários, atestados e encaminhamentos emitidos (não podem ser alterados depois de emitidos).</Muted>
        {can(P.CLINICAL_WRITE) && <Button onClick={() => setCreating(true)}>Emitir documento</Button>}
      </Row>
      <DataTable<ClinicalDocument>
        caption="Documentos emitidos"
        rows={docs.data}
        loading={docs.isPending}
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
          { key: "title", header: "Título", render: (d) => d.title },
          { key: "prof", header: "Profissional", hideOnMobile: true, render: (d) => d.professionalName },
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
              </Row>
            ),
          },
        ]}
      />
      {creating && <DocumentForm patientId={patientId} patientName={patientName} onClose={() => setCreating(false)} />}
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
  const save = useAction(() => api.clinical.addDocument(patientId, { professionalId, type, title, content }), {
    success: "Documento emitido.",
    invalidate: [["documents", patientId]],
    onSuccess: onClose,
  });

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
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => save.mutate(undefined)}
            loading={save.isPending}
            disabled={!professionalId || !content.trim()}
          >
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
          placeholder="Selecione…"
          value={professionalId}
          onChange={(e) => setProfessionalId(e.target.value)}
          options={(professionals.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
        />
        <FullRow>
          <TextField label="Título" maxLength={160} value={title} onChange={(e) => setTitle(e.target.value)} />
        </FullRow>
        <FullRow>
          <TextAreaField
            label="Conteúdo"
            rows={12}
            maxLength={20000}
            value={content}
            onChange={(e) => setContent(e.target.value)}
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
