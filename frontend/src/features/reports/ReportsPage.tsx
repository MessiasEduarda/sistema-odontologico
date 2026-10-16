"use client";

import { useQuery } from "@tanstack/react-query";
import { Download } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import {
  Badge,
  Card,
  CardTitle,
  ErrorState,
  KpiCard,
  KpiGrid,
  PageHeader,
  PermissionDenied,
  Row,
  Stack,
} from "@/components/ui/display";
import { LoadingState } from "@/components/ui/LoadingState";
import { Tabs } from "@/components/ui/Tabs";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import { formatDate, formatMoney, formatPercent, toDateInput } from "@/lib/format";
import { APPOINTMENT_STATUS } from "@/lib/labels";
import { useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { BarChart3, CalendarX2, Percent, Users } from "lucide-react";

export function ReportsPage() {
  const can = useCan();
  const today = new Date();
  const [from, setFrom] = useState(toDateInput(new Date(today.getFullYear(), today.getMonth(), 1)));
  const [to, setTo] = useState(toDateInput(today));
  const [tab, setTab] = useState("agenda");

  const items = [
    { id: "agenda", label: "Agenda e ocupação", content: <AgendaReportView from={from} to={to} /> },
    { id: "producao", label: "Produção", content: <ProductionView from={from} to={to} /> },
    can(P.FINANCE_READ) && { id: "financeiro", label: "Financeiro", content: <FinancialView from={from} to={to} /> },
    can(P.PATIENTS_READ) && { id: "pacientes", label: "Pacientes", content: <PatientsView /> },
  ].filter(Boolean) as { id: string; label: string; content: React.ReactNode }[];

  const exportable = ["agenda", "producao", "financeiro"].includes(tab);
  const type = tab === "agenda" ? "agenda" : tab === "producao" ? "production" : "financial";

  return (
    <Stack>
      <PageHeader
        title="Relatórios"
        actions={
          exportable && (
            <Button variant="outline" onClick={() => window.location.assign(api.reports.exportUrl(type, from, to))}>
              <Download size={16} aria-hidden /> Exportar CSV
            </Button>
          )
        }
      />
      <Row $gap={3}>
        <TextField label="De" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        <TextField label="Até" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
      </Row>
      <Tabs label="Relatórios" active={tab} onChange={setTab} items={items} />
    </Stack>
  );
}

function AgendaReportView({ from, to }: { from: string; to: string }) {
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["report-agenda", from, to],
    queryFn: () => api.reports.agenda(from, to),
  });
  if (isPending) return <LoadingState />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <Stack $gap={4}>
      <KpiGrid>
        <KpiCard icon={BarChart3} label="Agendamentos no período" value={data.total} />
        <KpiCard
          icon={CalendarX2}
          label="Taxa de faltas"
          value={formatPercent(data.noShowRate)}
          tone={data.noShowRate > 10 ? "danger" : "neutral"}
        />
        <KpiCard icon={Percent} label="Taxa de cancelamento" value={formatPercent(data.cancellationRate)} />
      </KpiGrid>
      <Row $gap={2}>
        {data.byStatus.map((s) => (
          <Badge key={s.status} $tone={APPOINTMENT_STATUS[s.status]?.tone}>
            {APPOINTMENT_STATUS[s.status]?.label}: {s.total}
          </Badge>
        ))}
      </Row>
      <DataTable
        caption="Ocupação por profissional"
        rows={data.byProfessional}
        rowKey={(p) => p.professionalId}
        emptyTitle="Sem dados no período"
        columns={[
          { key: "name", header: "Profissional", render: (p) => <strong>{p.professionalName}</strong> },
          { key: "appts", header: "Agendamentos", align: "right", render: (p) => p.appointments },
          { key: "done", header: "Concluídos", align: "right", render: (p) => p.completed },
          { key: "noshow", header: "Faltas", align: "right", render: (p) => p.noShows },
          { key: "cancel", header: "Cancelados", align: "right", hideOnMobile: true, render: (p) => p.cancelled },
          {
            key: "occ",
            header: "Ocupação",
            align: "right",
            render: (p) => (
              <span
                title={
                  p.availableMinutes
                    ? `${Math.round(p.bookedMinutes / 60)}h agendadas de ${Math.round(p.availableMinutes / 60)}h disponíveis`
                    : "Profissional sem horário de atendimento cadastrado (Cadastros)."
                }
              >
                {p.availableMinutes ? formatPercent(p.occupancyPercent) : "—"}
              </span>
            ),
          },
        ]}
      />
    </Stack>
  );
}

function ProductionView({ from, to }: { from: string; to: string }) {
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["report-production", from, to],
    queryFn: () => api.reports.production(from, to),
  });
  if (isPending) return <LoadingState />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <Stack $gap={4}>
      <KpiGrid>
        <KpiCard icon={BarChart3} label="Produção no período" value={formatMoney(data.totalValue)} tone="success" />
      </KpiGrid>
      <Card $padding={false}>
        <DataTable
          caption="Produção por profissional"
          rows={data.byProfessional}
          rowKey={(p) => p.professionalId}
          emptyTitle="Nenhum procedimento realizado no período"
          columns={[
            { key: "name", header: "Profissional", render: (p) => <strong>{p.professionalName}</strong> },
            { key: "count", header: "Procedimentos", align: "right", render: (p) => p.procedures },
            { key: "value", header: "Valor", align: "right", render: (p) => formatMoney(p.value) },
          ]}
        />
      </Card>
      <CardTitle>Procedimentos mais realizados</CardTitle>
      <DataTable
        caption="Procedimentos mais realizados"
        rows={data.topProcedures}
        rowKey={(p) => p.description}
        emptyTitle="Sem dados"
        columns={[
          { key: "desc", header: "Procedimento", render: (p) => p.description },
          { key: "total", header: "Quantidade", align: "right", render: (p) => p.total },
          { key: "value", header: "Valor", align: "right", render: (p) => formatMoney(p.value) },
        ]}
      />
      <small style={{ color: "#6B655F" }}>
        Valor: preço do item do orçamento quando vinculado; caso contrário, preço de tabela do procedimento.
      </small>
    </Stack>
  );
}

function FinancialView({ from, to }: { from: string; to: string }) {
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["report-financial", from, to],
    queryFn: () => api.reports.financial(from, to),
  });
  if (isPending) return <LoadingState />;
  if (error)
    return error.message.includes("permiss") ? <PermissionDenied /> : <ErrorState error={error} onRetry={refetch} />;
  return (
    <Stack $gap={4}>
      <DataTable
        caption="Movimento por mês"
        rows={data.months}
        rowKey={(m) => m.month}
        columns={[
          { key: "month", header: "Mês", render: (m) => m.month.split("-").reverse().join("/") },
          { key: "received", header: "Recebido", align: "right", render: (m) => formatMoney(m.received) },
          { key: "refunded", header: "Estornado", align: "right", render: (m) => formatMoney(m.refunded) },
          { key: "paid", header: "Pago", align: "right", render: (m) => formatMoney(m.paidOut) },
          {
            key: "net",
            header: "Resultado",
            align: "right",
            render: (m) => <strong>{formatMoney(Number(m.received) - Number(m.refunded) - Number(m.paidOut))}</strong>,
          },
        ]}
      />
      <CardTitle>Inadimplência por tempo de atraso</CardTitle>
      <DataTable
        caption="Inadimplência"
        rows={data.overdueAging}
        rowKey={(a) => a.bucket}
        emptyTitle="Nenhuma parcela em atraso"
        columns={[
          { key: "bucket", header: "Atraso", render: (a) => a.bucket },
          { key: "count", header: "Parcelas", align: "right", render: (a) => a.installments },
          { key: "amount", header: "Valor", align: "right", render: (a) => formatMoney(a.amount) },
        ]}
      />
    </Stack>
  );
}

function PatientsView() {
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["report-patients"],
    queryFn: api.reports.patients,
  });
  if (isPending) return <LoadingState />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <Stack $gap={4}>
      <KpiGrid>
        <KpiCard icon={Users} label="Pacientes ativos" value={data.active} />
        <KpiCard icon={Users} label="Inativos" value={data.inactive} />
      </KpiGrid>
      <DataTable
        caption="Novos pacientes por mês (12 meses)"
        rows={data.newByMonth}
        rowKey={(m) => m.month}
        emptyTitle="Sem cadastros no período"
        columns={[
          { key: "month", header: "Mês", render: (m) => m.month.split("-").reverse().join("/") },
          { key: "total", header: "Novos pacientes", align: "right", render: (m) => m.total },
        ]}
      />
      <CardTitle>Aniversariantes do mês</CardTitle>
      <DataTable
        caption="Aniversariantes do mês"
        rows={data.birthdaysThisMonth}
        rowKey={(b) => b.id}
        emptyTitle="Nenhum aniversariante"
        columns={[
          { key: "name", header: "Paciente", render: (b) => <Link href={`/pacientes/${b.id}`}>{b.fullName}</Link> },
          { key: "date", header: "Aniversário", render: (b) => formatDate(b.birthDate).slice(0, 5) },
          { key: "mobile", header: "Celular", render: (b) => b.mobile ?? "—" },
        ]}
      />
    </Stack>
  );
}
