"use client";

import { useQuery } from "@tanstack/react-query";
import { AlarmClock, Inbox, MessageSquareReply, Plus, Star } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { UnreadBadge, WithBadge } from "@/components/ui/UnreadBadge";
import { Badge, FilterBar, KpiCard, KpiGrid, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow, SelectField, TextAreaField } from "@/components/ui/fields";
import { Modal } from "@/components/ui/Modal";
import { SearchInput } from "@/components/ui/SearchInput";
import { Tabs } from "@/components/ui/Tabs";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { CannedResponse, PlatformTicketSummary } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { TICKET_PRIORITY, TICKET_STATUS } from "@/lib/labels";
import { useAction } from "@/lib/mutations";
import { useRequiredFields } from "@/lib/useRequiredFields";
import { TicketBadges } from "./TicketConversation";

const STATUS_OPTIONS = [
  { value: "", label: "Em aberto (todas)" },
  ...Object.entries(TICKET_STATUS).map(([value, s]) => ({ value, label: s.label })),
];

const PRIORITY_OPTIONS = [
  { value: "", label: "Todas" },
  ...Object.entries(TICKET_PRIORITY).map(([value, p]) => ({ value, label: p.label })),
];

export function PlatformSupportTab() {
  const [tab, setTab] = useState("fila");
  return (
    <Stack $gap={4}>
      <SupportMetricsCards />
      <Tabs
        label="Suporte"
        variant="sub"
        active={tab}
        onChange={setTab}
        items={[
          { id: "fila", label: "Fila de chamados", content: <Queue /> },
          { id: "respostas", label: "Respostas prontas", content: <CannedResponses /> },
        ]}
      />
    </Stack>
  );
}

function SupportMetricsCards() {
  const { data } = useQuery({ queryKey: ["support-metrics"], queryFn: api.platform.supportMetrics });
  if (!data) return null;
  return (
    <KpiGrid>
      <KpiCard
        icon={Inbox}
        label="Chamados em aberto"
        value={data.open}
        hint={`${data.urgentOpen} urgente(s) · ${data.waitingClinic} aguardando clínica`}
        tone={data.urgentOpen > 0 ? "danger" : "neutral"}
      />
      <KpiCard
        icon={AlarmClock}
        label="Prazo estourado"
        value={data.overdue}
        hint="Sem primeira resposta no prazo"
        tone={data.overdue > 0 ? "danger" : "success"}
      />
      <KpiCard
        icon={MessageSquareReply}
        label="Primeira resposta (média)"
        value={data.avgFirstResponseMinutes == null ? "-" : formatMinutes(data.avgFirstResponseMinutes)}
        hint={`${data.openedLast30Days} chamado(s) nos últimos 30 dias`}
      />
      <KpiCard
        icon={Star}
        label="Avaliação média"
        value={data.avgRating == null ? "-" : `${String(data.avgRating).replace(".", ",")} / 5`}
        hint={`${data.ratedCount} avaliação(ões)`}
      />
    </KpiGrid>
  );
}

function formatMinutes(minutes: number) {
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest ? `${hours}h${String(rest).padStart(2, "0")}` : `${hours}h`;
}

function Queue() {
  const router = useRouter();
  const [status, setStatus] = useState("");
  const [priority, setPriority] = useState("");
  const [q, setQ] = useState("");
  const [onlyUnread, setOnlyUnread] = useState(false);
  const tickets = useQuery({
    queryKey: ["platform-support-tickets", status, priority, q, onlyUnread],
    queryFn: () =>
      api.platform.supportTickets({
        status: status || undefined,
        priority: priority || undefined,
        q: q || undefined,
        unread: onlyUnread || undefined,
      }),
    refetchInterval: 60_000,
  });
  const rows = status ? tickets.data : tickets.data?.filter((t) => t.status !== "CLOSED" && t.status !== "RESOLVED");

  return (
    <Stack $gap={4}>
      <FilterBar>
        <SearchInput label="Buscar" placeholder="Protocolo, assunto ou clínica…" value={q} onChange={setQ} />
        <div style={{ width: 220 }}>
          <SelectField
            label="Situação"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            options={STATUS_OPTIONS}
          />
        </div>
        <div style={{ width: 180 }}>
          <SelectField
            label="Prioridade"
            value={priority}
            onChange={(e) => setPriority(e.target.value)}
            options={PRIORITY_OPTIONS}
          />
        </div>
        <CheckboxField
          label="Só com mensagem nova"
          checked={onlyUnread}
          onChange={(e) => setOnlyUnread(e.target.checked)}
        />
      </FilterBar>
      <DataTable<PlatformTicketSummary>
        caption="Fila de chamados"
        rows={rows}
        loading={tickets.isPending}
        error={tickets.error}
        onRetry={tickets.refetch}
        rowKey={(t) => t.id}
        rowHighlight={(t) => t.unreadCount > 0}
        onRowClick={(t) => router.push(`/plataforma/suporte/${t.id}`)}
        emptyTitle="Nenhum chamado"
        emptyDescription="Quando uma clínica abrir um chamado, ele aparece aqui e você recebe um e-mail."
        columns={[
          {
            key: "protocol",
            header: "Protocolo",
            render: (t) => (
              <WithBadge>
                <strong>{t.protocol}</strong>
                <UnreadBadge count={t.unreadCount} announce />
              </WithBadge>
            ),
          },
          { key: "clinic", header: "Clínica", render: (t) => t.clinicName },
          { key: "subject", header: "Assunto", hideOnMobile: true, render: (t) => t.subject },
          { key: "status", header: "Situação", render: (t) => <TicketBadges ticket={t} /> },
          {
            key: "due",
            header: "1ª resposta até",
            hideOnMobile: true,
            render: (t) => (t.firstResponseAt ? "Respondido" : formatDateTime(t.firstResponseDueAt)),
          },
          {
            key: "activity",
            header: "Última atualização",
            hideOnMobile: true,
            render: (t) => formatDateTime(t.lastActivityAt),
          },
        ]}
      />
    </Stack>
  );
}

function CannedResponses() {
  const [editing, setEditing] = useState<CannedResponse | "new" | null>(null);
  const list = useQuery({ queryKey: ["canned-responses"], queryFn: () => api.platform.cannedResponses() });
  return (
    <Stack $gap={4}>
      <Row $justify="space-between">
        <Alert tone="info">Textos que você reaproveita ao responder chamados (ex.: como redefinir a senha).</Alert>
        <Button onClick={() => setEditing("new")}>
          <Plus size={16} aria-hidden /> Nova resposta pronta
        </Button>
      </Row>
      <DataTable<CannedResponse>
        caption="Respostas prontas"
        rows={list.data}
        loading={list.isPending}
        error={list.error}
        onRetry={list.refetch}
        rowKey={(c) => c.id}
        onRowClick={(c) => setEditing(c)}
        emptyTitle="Nenhuma resposta pronta"
        columns={[
          { key: "title", header: "Título", render: (c) => <strong>{c.title}</strong> },
          {
            key: "body",
            header: "Texto",
            hideOnMobile: true,
            render: (c) => (c.body.length > 120 ? `${c.body.slice(0, 120)}…` : c.body),
          },
          {
            key: "active",
            header: "Situação",
            render: (c) => (c.active ? <Badge $tone="success">Ativa</Badge> : <Badge>Inativa</Badge>),
          },
        ]}
      />
      {editing && <CannedResponseForm response={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </Stack>
  );
}

function CannedResponseForm({ response, onClose }: { response: CannedResponse | null; onClose: () => void }) {
  const [title, setTitle] = useState(response?.title ?? "");
  const [body, setBody] = useState(response?.body ?? "");
  const [active, setActive] = useState(response?.active ?? true);
  const form = useRequiredFields({
    title: !title.trim() && "Informe o título.",
    body: !body.trim() && "Escreva o texto da resposta.",
  });
  const save = useAction(
    () => {
      const input = { title: title.trim(), body: body.trim(), active };
      return response
        ? api.platform.updateCannedResponse(response.id, input)
        : api.platform.createCannedResponse(input);
    },
    { success: "Resposta pronta salva com sucesso!", invalidate: [["canned-responses"]], onSuccess: onClose },
  );
  return (
    <Modal
      open
      onClose={onClose}
      title={response ? "Editar resposta pronta" : "Nova resposta pronta"}
      size="lg"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => form.check() && save.mutate(undefined)} loading={save.isPending}>
            Salvar
          </Button>
        </>
      }
    >
      <FormGrid $columns={1}>
        <TextField
          label="Título"
          required
          maxLength={120}
          error={form.errors.title}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
        <TextAreaField
          label="Texto"
          required
          rows={8}
          maxLength={5000}
          error={form.errors.body}
          value={body}
          onChange={(e) => setBody(e.target.value)}
        />
        <FullRow>
          <CheckboxField label="Ativa" checked={active} onChange={(e) => setActive(e.target.checked)} />
        </FullRow>
      </FormGrid>
    </Modal>
  );
}
