"use client";

import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, CalendarCheck, CalendarDays, DollarSign, PackageX, UserPlus, Users } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Card, CardTitle, ErrorState, KpiCard, KpiGrid, PageHeader, Stack } from "@/components/ui/display";
import { LoadingState } from "@/components/ui/LoadingState";
import { useMe } from "@/features/auth/hooks";
import { api } from "@/lib/api/endpoints";
import type { Dashboard } from "@/lib/api/types";
import { formatMoney, formatTime } from "@/lib/format";
import { APPOINTMENT_STATUS } from "@/lib/labels";
import { useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";

type TodayRow = Dashboard["today"][number];

export function DashboardPage() {
  const { data: me } = useMe();
  const can = useCan();
  const router = useRouter();
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["dashboard"],
    queryFn: api.dashboard,
    enabled: Boolean(me?.activeClinic),
    refetchInterval: 60_000,
  });

  if (me?.platformAdmin && !me.activeClinic) {
    return (
      <Stack>
        <PageHeader title={`Olá, ${me.user.name.split(" ")[0]}`} description="Você é administrador da plataforma." />
        <Card>
          <p style={{ marginBottom: 16 }}>Gerencie clínicas, planos e assinaturas na área da plataforma.</p>
          <Button onClick={() => router.push("/plataforma")}>Ir para a plataforma</Button>
        </Card>
      </Stack>
    );
  }
  if (me && !me.activeClinic) {
    return (
      <Stack>
        <PageHeader title={`Olá, ${me.user.name.split(" ")[0]}`} />
        <Alert tone="info">Você ainda não tem acesso a nenhuma clínica. Peça um convite ao responsável.</Alert>
      </Stack>
    );
  }
  if (isPending) return <LoadingState />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;

  const today = new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "numeric", month: "long" }).format(new Date());

  return (
    <Stack>
      <PageHeader
        title="Dashboard"
        description={`${me?.activeClinic?.name ?? ""} · ${today}`}
        actions={
          can(P.SCHEDULE_WRITE) && (
            <Button onClick={() => router.push("/agenda?novo=1")}>
              <CalendarDays size={18} aria-hidden /> Novo agendamento
            </Button>
          )
        }
      />

      {data.alerts.length > 0 && (
        <Stack $gap={2}>
          {data.alerts.map((a) => (
            <Alert key={a.type} tone={a.severity}>
              <AlertLink href={alertHref(a.type)}>{a.message}</AlertLink>
            </Alert>
          ))}
        </Stack>
      )}

      <KpiGrid>
        {data.appointmentsToday !== null && (
          <KpiCard icon={CalendarDays} label="Atendimentos hoje" value={data.appointmentsToday} />
        )}
        {data.confirmedToday !== null && (
          <KpiCard
            icon={CalendarCheck}
            label="Confirmados / presentes"
            value={`${data.confirmedToday} de ${data.appointmentsToday ?? 0}`}
          />
        )}
        {data.activePatients !== null && <KpiCard icon={Users} label="Pacientes ativos" value={data.activePatients} />}
        {data.newPatientsThisMonth !== null && (
          <KpiCard icon={UserPlus} label="Novos pacientes no mês" value={data.newPatientsThisMonth} />
        )}
        {data.receivedThisMonth !== null && (
          <KpiCard
            icon={DollarSign}
            label="Recebido no mês"
            value={formatMoney(data.receivedThisMonth)}
            tone="success"
          />
        )}
        {data.overdueAmount !== null && (
          <KpiCard
            icon={AlertTriangle}
            label="Em atraso"
            value={formatMoney(data.overdueAmount)}
            tone={data.overdueAmount > 0 ? "danger" : "neutral"}
            hint={data.overdueAmount > 0 ? "Atenção!" : undefined}
          />
        )}
        {data.lowStockProducts !== null && (
          <KpiCard
            icon={PackageX}
            label="Estoque baixo"
            value={data.lowStockProducts}
            tone={data.lowStockProducts > 0 ? "danger" : "neutral"}
          />
        )}
      </KpiGrid>

      {data.appointmentsToday !== null && (
        <Card $padding={false}>
          <Header>
            <CardTitle as="h2">Agenda de hoje</CardTitle>
            <Link href="/agenda">Ver agenda completa</Link>
          </Header>
          <DataTable<TodayRow>
            caption="Atendimentos de hoje"
            rows={data.today}
            rowKey={(r) => r.id}
            onRowClick={(r) => router.push(`/pacientes/${r.patientId}`)}
            emptyTitle="Nenhum atendimento hoje"
            columns={[
              {
                key: "time",
                header: "Horário",
                minWidth: "110px",
                render: (r) => `${formatTime(r.startsAt)} – ${formatTime(r.endsAt)}`,
              },
              { key: "patient", header: "Paciente", render: (r) => <strong>{r.patientName}</strong> },
              {
                key: "professional",
                header: "Profissional",
                render: (r) => (
                  <ProfessionalCell>
                    <Dot style={{ background: r.professionalColor }} aria-hidden />
                    {r.professionalName}
                  </ProfessionalCell>
                ),
              },
              { key: "procedure", header: "Procedimento", hideOnMobile: true, render: (r) => r.procedureName ?? "—" },
              {
                key: "status",
                header: "Status",
                render: (r) => (
                  <Badge $tone={APPOINTMENT_STATUS[r.status]?.tone}>{APPOINTMENT_STATUS[r.status]?.label}</Badge>
                ),
              },
            ]}
          />
        </Card>
      )}
    </Stack>
  );
}

function alertHref(type: string) {
  switch (type) {
    case "UNCONFIRMED_TOMORROW":
      return "/agenda?dia=amanha";
    case "OVERDUE_INSTALLMENTS":
      return "/financeiro?aba=receber&filtro=OVERDUE";
    case "PAYABLES_DUE":
      return "/financeiro?aba=pagar";
    case "LOW_STOCK":
      return "/estoque?baixo=1";
    default:
      return "/pacientes";
  }
}

const Header = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: ${({ theme }) => `${theme.space[5]} ${theme.space[6]} 0`};

  h2 {
    margin-bottom: ${({ theme }) => theme.space[4]};
  }
  a {
    font-size: ${({ theme }) => theme.fontSizes.sm};
    font-weight: ${({ theme }) => theme.fontWeights.semibold};
  }
`;

const AlertLink = styled(Link)`
  color: inherit;
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
`;

const ProfessionalCell = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 8px;
`;

const Dot = styled.span`
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
`;
