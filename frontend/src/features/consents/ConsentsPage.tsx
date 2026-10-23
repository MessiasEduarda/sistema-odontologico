"use client";

import { useQuery } from "@tanstack/react-query";
import { FileSignature } from "lucide-react";
import { useState } from "react";
import { PdfButton } from "@/components/domain/PdfButton";
import { PatientSelect } from "@/components/domain/PatientSelect";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, FilterBar, KpiCard, KpiGrid, PageHeader, Stack } from "@/components/ui/display";
import { SelectField } from "@/components/ui/fields";
import { Modal } from "@/components/ui/Modal";
import { SearchInput } from "@/components/ui/SearchInput";
import { Tabs } from "@/components/ui/Tabs";
import { ConsentDetail, IssueConsent } from "@/features/clinical/ConsentsSection";
import { api } from "@/lib/api/endpoints";
import type { ConsentListItem } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { CONSENT_CATEGORIES, CONSENT_STATUS } from "@/lib/labels";
import { useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { useRequiredFields } from "@/lib/useRequiredFields";
import { CATEGORY_OPTIONS, ConsentTemplatesManager } from "./ConsentTemplatesManager";

const STATUS_OPTIONS = Object.entries(CONSENT_STATUS).map(([value, s]) => ({ value, label: s.label }));

export function ConsentsPage() {
  const can = useCan();
  const [tab, setTab] = useState<string | null>(null);
  const items = [
    { id: "emitidos", label: "Termos dos pacientes", content: <IssuedTab /> },
    can(P.CLINIC_SETTINGS_MANAGE) && { id: "modelos", label: "Modelos", content: <ConsentTemplatesManager /> },
  ].filter(Boolean) as { id: string; label: string; content: React.ReactNode }[];
  return (
    <Stack>
      <PageHeader
        title="Termos de consentimento"
        description="Odontologia, harmonização, uso de imagem e privacidade (LGPD). O texto fica congelado na emissão."
      />
      <Tabs label="Termos" active={tab ?? items[0]?.id ?? ""} onChange={setTab} items={items} />
    </Stack>
  );
}

function IssuedTab() {
  const can = useCan();
  const writable = can(P.CLINICAL_WRITE, P.PATIENTS_WRITE);
  const [status, setStatus] = useState("");
  const [category, setCategory] = useState("");
  const [q, setQ] = useState("");
  const [open, setOpen] = useState<ConsentListItem | null>(null);
  const [choosing, setChoosing] = useState(false);
  const [issueFor, setIssueFor] = useState<{ id: string; name: string } | null>(null);
  const list = useQuery({
    queryKey: ["all-consents", status, category, q],
    queryFn: () => api.clinical.allConsents({ status, category, q }),
  });
  const all = useQuery({ queryKey: ["all-consents", "", "", ""], queryFn: () => api.clinical.allConsents({}) });
  const count = (s: string) => (all.data ?? []).filter((c) => c.status === s).length;

  return (
    <Stack $gap={4}>
      <KpiGrid>
        <KpiCard icon={FileSignature} label="Aguardando assinatura" value={count("PENDING")} tone="danger" />
        <KpiCard icon={FileSignature} label="Assinados" value={count("SIGNED")} tone="success" />
        <KpiCard icon={FileSignature} label="Revogados" value={count("REVOKED")} />
      </KpiGrid>
      <FilterBar $justify="space-between">
        <FilterBar>
          <SearchInput label="Paciente" placeholder="Nome do paciente…" value={q} onChange={setQ} />
          <div style={{ width: 200 }}>
            <SelectField
              label="Situação"
              placeholder="Todas"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              options={STATUS_OPTIONS}
            />
          </div>
          <div style={{ width: 200 }}>
            <SelectField
              label="Categoria"
              placeholder="Todas"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              options={CATEGORY_OPTIONS}
            />
          </div>
        </FilterBar>
        {writable && <Button onClick={() => setChoosing(true)}>Emitir termo</Button>}
      </FilterBar>
      <DataTable<ConsentListItem>
        caption="Termos dos pacientes"
        rows={list.data}
        loading={list.isPending}
        error={list.error}
        onRetry={list.refetch}
        rowKey={(c) => c.id}
        onRowClick={setOpen}
        emptyTitle="Nenhum termo encontrado"
        columns={[
          { key: "date", header: "Emissão", render: (c) => formatDateTime(c.createdAt) },
          { key: "patient", header: "Paciente", render: (c) => <strong>{c.patientName}</strong> },
          { key: "title", header: "Termo", render: (c) => c.title },
          {
            key: "category",
            header: "Categoria",
            hideOnMobile: true,
            render: (c) => (
              <Badge $tone={CONSENT_CATEGORIES[c.category]?.tone}>{CONSENT_CATEGORIES[c.category]?.label}</Badge>
            ),
          },
          { key: "proc", header: "Procedimento", hideOnMobile: true, render: (c) => c.procedureDescription ?? "—" },
          {
            key: "status",
            header: "Situação",
            render: (c) => <Badge $tone={CONSENT_STATUS[c.status]?.tone}>{CONSENT_STATUS[c.status]?.label}</Badge>,
          },
          {
            key: "pdf",
            header: "",
            render: (c) => (
              <PdfButton
                compact
                load={async () => ({
                  kind: "consent",
                  patientId: c.patientId,
                  data: await api.clinical.consent(c.patientId, c.id),
                })}
              />
            ),
          },
        ]}
      />
      {open && (
        <ConsentDetail
          patientId={open.patientId}
          patientName={open.patientName}
          consentId={open.id}
          writable={writable}
          onClose={() => setOpen(null)}
        />
      )}
      <ChoosePatient
        open={choosing}
        onClose={() => setChoosing(false)}
        onChosen={(p) => {
          setChoosing(false);
          setIssueFor(p);
        }}
      />
      {issueFor && (
        <IssueConsent
          patientId={issueFor.id}
          onClose={() => setIssueFor(null)}
          onIssued={(id) => {
            const p = issueFor;
            setOpen({
              id,
              patientId: p.id,
              patientName: p.name,
              title: "",
              category: "DENTAL",
              status: "PENDING",
              professionalName: null,
              procedureDescription: null,
              createdAt: new Date().toISOString(),
              signedAt: null,
            });
          }}
        />
      )}
    </Stack>
  );
}

function ChoosePatient({
  open,
  onClose,
  onChosen,
}: {
  open: boolean;
  onClose: () => void;
  onChosen: (p: { id: string; name: string }) => void;
}) {
  const [patient, setPatient] = useState<{ id: string; name: string } | null>(null);
  const form = useRequiredFields({ patient: !patient && "Selecione o paciente." });
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Emitir termo: escolha o paciente"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => form.check() && patient && onChosen(patient)}>Continuar</Button>
        </>
      }
    >
      <PatientSelect value={patient} onChange={setPatient} required error={form.errors.patient} />
    </Modal>
  );
}
