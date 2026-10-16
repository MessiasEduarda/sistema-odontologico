"use client";

import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { PdfButton } from "@/components/domain/PdfButton";
import { ReasonDialog } from "@/components/domain/ReasonDialog";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, DetailList, EmptyState, PageHeader, Row, Stack } from "@/components/ui/display";
import { Modal } from "@/components/ui/Modal";
import { SearchInput } from "@/components/ui/SearchInput";
import { Tabs } from "@/components/ui/Tabs";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { AestheticRecord } from "@/lib/api/types";
import { formatDate, formatDateTime, toDateInput } from "@/lib/format";
import { AESTHETIC_TYPES, AESTHETIC_UNITS, CONSENT_STATUS } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { AestheticForm } from "./AestheticForm";

/** A clínica realiza harmonização orofacial? (opção em Configurações). */
export function useAestheticsEnabled() {
  const settings = useQuery({ queryKey: ["clinic-settings"], queryFn: api.clinic.settings, staleTime: 300_000 });
  // Mesma chave usada pelo menu lateral e pela página de Configurações.
  return { enabled: settings.data?.aestheticsEnabled ?? false, loading: settings.isPending };
}

const fmtQty = (r: AestheticRecord) => `${Number(r.totalQuantity).toLocaleString("pt-BR")} ${AESTHETIC_UNITS[r.unit]}`;

export function AestheticsPage() {
  const can = useCan();
  const { enabled, loading } = useAestheticsEnabled();
  const [tab, setTab] = useState("aplicacoes");
  const [creating, setCreating] = useState(false);

  if (loading) return null;
  if (!enabled) {
    return (
      <Stack>
        <PageHeader title="Harmonização orofacial" />
        <EmptyState
          title="Harmonização desativada"
          description="Se a clínica realiza toxina botulínica, preenchimentos e outros procedimentos de harmonização, ative a opção em Configurações. Isso libera a ficha de anamnese específica, os termos e o registro das aplicações."
          action={
            can(P.CLINIC_SETTINGS_MANAGE) && (
              <Link href="/configuracoes">
                <Button>Ir para Configurações</Button>
              </Link>
            )
          }
        />
      </Stack>
    );
  }

  return (
    <Stack>
      <PageHeader
        title="Harmonização orofacial"
        description="Registro das aplicações com produto, lote e validade, retornos e rastreabilidade."
        actions={
          can(P.CLINICAL_WRITE) && (
            <Button onClick={() => setCreating(true)}>
              <Plus size={18} aria-hidden /> Registrar aplicação
            </Button>
          )
        }
      />
      <Alert tone="info">
        Antes de cada procedimento: ficha de anamnese de harmonização (página Anamnese), termo de consentimento assinado
        (página Termos) e orientações entregues ao paciente (página Orientações).
      </Alert>
      <Tabs
        label="Harmonização"
        active={tab}
        onChange={setTab}
        items={[
          { id: "aplicacoes", label: "Aplicações", content: <ApplicationsTab /> },
          { id: "retornos", label: "Retornos", content: <ReturnsTab /> },
          { id: "lote", label: "Buscar por lote", content: <BatchTab /> },
        ]}
      />
      {creating && <AestheticForm onClose={() => setCreating(false)} />}
    </Stack>
  );
}

function ApplicationsTab() {
  const [q, setQ] = useState("");
  const list = useQuery({
    queryKey: ["aesthetics-search", "all", q],
    queryFn: () => api.clinical.searchAesthetics({ q }),
  });
  return (
    <Stack $gap={4}>
      <SearchInput label="Buscar" placeholder="Paciente ou produto…" value={q} onChange={setQ} />
      <AestheticTable rows={list.data} loading={list.isPending} showPatient />
    </Stack>
  );
}

function ReturnsTab() {
  const [until, setUntil] = useState(() => toDateInput(new Date(Date.now() + 30 * 86_400_000)));
  const list = useQuery({
    queryKey: ["aesthetics-search", "returns", until],
    queryFn: () => api.clinical.searchAesthetics({ returnsUntil: until }),
  });
  const today = toDateInput(new Date());
  return (
    <Stack $gap={4}>
      <div style={{ width: 220 }}>
        <TextField label="Retornos até" type="date" value={until} onChange={(e) => setUntil(e.target.value)} />
      </div>
      <DataTable<AestheticRecord>
        caption="Retornos previstos"
        rows={list.data}
        loading={list.isPending}
        rowKey={(r) => r.id}
        emptyTitle="Nenhum retorno no período"
        columns={[
          {
            key: "return",
            header: "Retorno",
            render: (r) => (
              <Badge $tone={(r.returnDate ?? "") < today ? "danger" : "warning"}>{formatDate(r.returnDate)}</Badge>
            ),
          },
          {
            key: "patient",
            header: "Paciente",
            render: (r) => <Link href={`/pacientes/${r.patientId}`}>{r.patientName}</Link>,
          },
          { key: "proc", header: "Procedimento", render: (r) => AESTHETIC_TYPES[r.procedureType] ?? r.procedureType },
          { key: "date", header: "Aplicação", hideOnMobile: true, render: (r) => formatDate(r.performedAt) },
          { key: "prof", header: "Profissional", hideOnMobile: true, render: (r) => r.professionalName },
        ]}
      />
    </Stack>
  );
}

function BatchTab() {
  const [batch, setBatch] = useState("");
  const [search, setSearch] = useState("");
  const list = useQuery({
    queryKey: ["aesthetics-search", "batch", search],
    queryFn: () => api.clinical.searchAesthetics({ batch: search }),
    enabled: search.length > 0,
  });
  return (
    <Stack $gap={4}>
      <Alert tone="info">
        Informe o lote exato para listar todos os pacientes que receberam aquele produto (ex.: em caso de alerta do
        fabricante ou da ANVISA).
      </Alert>
      <Row $gap={3}>
        <div style={{ width: 260 }}>
          <TextField label="Lote" value={batch} onChange={(e) => setBatch(e.target.value)} maxLength={60} />
        </div>
        <Button onClick={() => setSearch(batch.trim())} disabled={!batch.trim()}>
          Buscar
        </Button>
      </Row>
      {search && <AestheticTable rows={list.data} loading={list.isPending} showPatient />}
    </Stack>
  );
}

/** Tabela de aplicações; clique abre o detalhe (com anulação). */
export function AestheticTable({
  rows,
  loading,
  showPatient,
}: {
  rows: AestheticRecord[] | undefined;
  loading: boolean;
  showPatient?: boolean;
}) {
  const [open, setOpen] = useState<AestheticRecord | null>(null);
  return (
    <>
      <DataTable<AestheticRecord>
        caption="Aplicações de harmonização"
        rows={rows}
        loading={loading}
        rowKey={(r) => r.id}
        onRowClick={setOpen}
        emptyTitle="Nenhuma aplicação registrada"
        columns={[
          { key: "date", header: "Data", render: (r) => formatDate(r.performedAt) },
          ...(showPatient
            ? [{ key: "patient", header: "Paciente", render: (r: AestheticRecord) => <strong>{r.patientName}</strong> }]
            : []),
          { key: "proc", header: "Procedimento", render: (r) => AESTHETIC_TYPES[r.procedureType] ?? r.procedureType },
          { key: "product", header: "Produto", hideOnMobile: true, render: (r) => r.productName },
          { key: "batch", header: "Lote", render: (r) => r.batch },
          { key: "qty", header: "Total", align: "right", render: fmtQty },
          {
            key: "status",
            header: "Situação",
            render: (r) =>
              r.voidedAt ? (
                <Badge>Anulado</Badge>
              ) : r.consentStatus === "SIGNED" ? (
                <Badge $tone="success">Termo assinado</Badge>
              ) : (
                <Badge $tone="warning">Sem termo assinado</Badge>
              ),
          },
          {
            key: "pdf",
            header: "",
            render: (r) => <PdfButton compact load={() => ({ kind: "aesthetic", patientId: r.patientId, data: r })} />,
          },
        ]}
      />
      {open && <AestheticDetail record={open} onClose={() => setOpen(null)} />}
    </>
  );
}

function AestheticDetail({ record: r, onClose }: { record: AestheticRecord; onClose: () => void }) {
  const can = useCan();
  const [voiding, setVoiding] = useState(false);
  const voidIt = useAction((reason: string) => api.clinical.voidAesthetic(r.patientId, r.id, reason), {
    success: "Registro anulado.",
    invalidate: [["aesthetics"], ["aesthetics-search"]],
    onSuccess: onClose,
  });
  return (
    <>
      <Modal
        open
        onClose={onClose}
        title={`${AESTHETIC_TYPES[r.procedureType] ?? r.procedureType} — ${formatDate(r.performedAt)}`}
        size="lg"
        footer={
          <>
            {can(P.CLINICAL_WRITE) && !r.voidedAt && (
              <Button variant="outline" onClick={() => setVoiding(true)}>
                Anular registro
              </Button>
            )}
            <PdfButton variant="primary" load={() => ({ kind: "aesthetic", patientId: r.patientId, data: r })} />
          </>
        }
      >
        <Stack $gap={4}>
          {r.voidedAt && (
            <Alert tone="info">
              Anulado em {formatDateTime(r.voidedAt)}: {r.voidReason}
            </Alert>
          )}
          <DetailList>
            <div>
              <dt>Paciente</dt>
              <dd>
                <Link href={`/pacientes/${r.patientId}`}>{r.patientName}</Link>
              </dd>
            </div>
            <div>
              <dt>Profissional</dt>
              <dd>{r.professionalName}</dd>
            </div>
            <div>
              <dt>Data e hora</dt>
              <dd>{formatDateTime(r.performedAt)}</dd>
            </div>
            <div>
              <dt>Produto</dt>
              <dd>
                {r.productName}
                {r.manufacturer ? ` · ${r.manufacturer}` : ""}
              </dd>
            </div>
            <div>
              <dt>Lote · validade</dt>
              <dd>
                {r.batch} · {formatDate(r.expiryDate)}
              </dd>
            </div>
            <div>
              <dt>Registro ANVISA</dt>
              <dd>{r.anvisaRegistry ?? "—"}</dd>
            </div>
            <div>
              <dt>Termo</dt>
              <dd>{r.consentStatus ? CONSENT_STATUS[r.consentStatus]?.label : "Não vinculado"}</dd>
            </div>
            <div>
              <dt>Retorno</dt>
              <dd>{formatDate(r.returnDate)}</dd>
            </div>
          </DetailList>
          <DataTable
            caption="Regiões"
            rows={r.regions.map((x, i) => ({ ...x, key: String(i) }))}
            rowKey={(x) => x.key}
            columns={[
              { key: "region", header: "Região", render: (x) => x.region },
              {
                key: "qty",
                header: "Quantidade",
                align: "right",
                render: (x) => `${Number(x.quantity).toLocaleString("pt-BR")} ${AESTHETIC_UNITS[r.unit]}`,
              },
              { key: "notes", header: "Observação", render: (x) => x.notes ?? "—" },
            ]}
          />
          <p>
            <strong>Total:</strong> {fmtQty(r)}
          </p>
          {r.technique && (
            <p style={{ whiteSpace: "pre-wrap" }}>
              <strong>Técnica:</strong> {r.technique}
            </p>
          )}
          {r.notes && (
            <p style={{ whiteSpace: "pre-wrap" }}>
              <strong>Observações:</strong> {r.notes}
            </p>
          )}
          <small>Registrado por {r.createdByName ?? "—"}</small>
        </Stack>
      </Modal>
      <ReasonDialog
        open={voiding}
        title="Anular registro de aplicação"
        confirmLabel="Anular"
        loading={voidIt.isPending}
        onConfirm={(reason) => voidIt.mutate(reason)}
        onClose={() => setVoiding(false)}
      />
    </>
  );
}

/** Aba “Harmonização” na ficha do paciente. */
export function AestheticsSection({ patientId, patientName }: { patientId: string; patientName: string }) {
  const can = useCan();
  const [creating, setCreating] = useState(false);
  const list = useQuery({ queryKey: ["aesthetics", patientId], queryFn: () => api.clinical.aesthetics(patientId) });
  return (
    <Stack $gap={4}>
      {can(P.CLINICAL_WRITE) && (
        <Row $justify="flex-end">
          <Button onClick={() => setCreating(true)}>
            <Plus size={16} aria-hidden /> Registrar aplicação
          </Button>
        </Row>
      )}
      <AestheticTable rows={list.data} loading={list.isPending} />
      {creating && <AestheticForm patient={{ id: patientId, name: patientName }} onClose={() => setCreating(false)} />}
    </Stack>
  );
}
