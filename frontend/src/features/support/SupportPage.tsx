"use client";

import { useQuery } from "@tanstack/react-query";
import { Clock, LifeBuoy, Mail, Plus } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import styled from "styled-components";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { UnreadBadge, WithBadge } from "@/components/ui/UnreadBadge";
import { Card, FilterBar, Muted, PageHeader, Stack } from "@/components/ui/display";
import { SelectField } from "@/components/ui/fields";
import { SearchInput, useRowSearch } from "@/components/ui/SearchInput";
import { useMe } from "@/features/auth/hooks";
import { api } from "@/lib/api/endpoints";
import type { TicketSummary } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { TICKET_STATUS, ticketCategoryLabel } from "@/lib/labels";
import { P } from "@/lib/permissions";
import { takeProblemReport, type ProblemReport } from "@/lib/support-report";
import { NewTicketForm } from "./NewTicketForm";
import { TicketBadges } from "./TicketConversation";

const STATUS_OPTIONS = [
  { value: "", label: "Todas as situações" },
  ...Object.entries(TICKET_STATUS).map(([value, s]) => ({ value, label: s.label })),
];

export function SupportPage() {
  const router = useRouter();
  const params = useSearchParams();
  const { data: me } = useMe();
  const [status, setStatus] = useState("");
  const [creating, setCreating] = useState(false);
  const [report, setReport] = useState<ProblemReport | null>(null);
  const manager = me?.permissions.includes(P.CLINIC_SETTINGS_MANAGE) ?? false;

  useEffect(() => {
    if (params.get("relatar") !== "1") return;
    const pending = takeProblemReport();
    queueMicrotask(() => {
      setReport(pending);
      setCreating(true);
    });
    router.replace("/suporte");
  }, [params, router]);

  const tickets = useQuery({
    queryKey: ["support-tickets", status],
    queryFn: () => api.support.tickets(status || undefined),
  });
  const contact = useQuery({ queryKey: ["support-contact"], queryFn: api.support.contact, staleTime: 3_600_000 });
  const search = useRowSearch(tickets.data, (t) => [t.protocol, t.subject, t.openedByName]);

  return (
    <Stack>
      <PageHeader
        title="Suporte"
        description={
          manager ? "Chamados da clínica com a equipe do Odonto SaaS." : "Seus chamados com a equipe do Odonto SaaS."
        }
        actions={
          <Button
            onClick={() => {
              setReport(null);
              setCreating(true);
            }}
          >
            <Plus size={16} aria-hidden /> Abrir chamado
          </Button>
        }
      />

      <InfoCard>
        <Info>
          <LifeBuoy size={20} aria-hidden />
          <span>
            <strong>Como funciona</strong>
            <Muted>
              Abra um chamado, acompanhe a resposta aqui e por e-mail. Urgente (não consegue atender pacientes): até 2
              horas úteis. Dúvidas: até 1 dia útil.
            </Muted>
          </span>
        </Info>
        <Info>
          <Clock size={20} aria-hidden />
          <span>
            <strong>Horário de atendimento</strong>
            <Muted>{contact.data?.hours ?? "segunda a sexta, das 8h às 18h"}</Muted>
          </span>
        </Info>
        {contact.data?.email && (
          <Info>
            <Mail size={20} aria-hidden />
            <span>
              <strong>Se o sistema não abrir</strong>
              <Muted>
                Escreva para <a href={`mailto:${contact.data.email}`}>{contact.data.email}</a>
              </Muted>
            </span>
          </Info>
        )}
      </InfoCard>

      <FilterBar>
        <SearchInput
          label="Buscar chamado"
          placeholder="Protocolo, assunto ou nome…"
          value={search.query}
          onChange={search.setQuery}
        />
        <div style={{ width: 240 }}>
          <SelectField
            label="Situação"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            options={STATUS_OPTIONS}
          />
        </div>
      </FilterBar>

      <DataTable<TicketSummary>
        caption="Chamados"
        rows={search.rows}
        loading={tickets.isPending}
        error={tickets.error}
        onRetry={tickets.refetch}
        rowKey={(t) => t.id}
        rowHighlight={(t) => t.unreadCount > 0}
        onRowClick={(t) => router.push(`/suporte/${t.id}`)}
        emptyTitle="Nenhum chamado"
        emptyDescription="Quando precisar de ajuda, clique em “Abrir chamado”."
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
          { key: "subject", header: "Assunto", render: (t) => t.subject },
          { key: "category", header: "Categoria", hideOnMobile: true, render: (t) => ticketCategoryLabel(t.category) },
          ...(manager
            ? [{ key: "by", header: "Aberto por", hideOnMobile: true, render: (t: TicketSummary) => t.openedByName }]
            : []),
          { key: "status", header: "Situação", render: (t) => <TicketBadges ticket={t} /> },
          {
            key: "activity",
            header: "Última atualização",
            hideOnMobile: true,
            render: (t) => formatDateTime(t.lastActivityAt),
          },
        ]}
      />

      {creating && <NewTicketForm report={report} onClose={() => setCreating(false)} />}
    </Stack>
  );
}

const InfoCard = styled(Card)`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: ${({ theme }) => theme.space[4]};
`;

const Info = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.space[3]};
  align-items: flex-start;

  svg {
    flex: none;
    margin-top: 2px;
    color: ${({ theme }) => theme.colors.primaryText};
  }
  span {
    display: grid;
    gap: ${({ theme }) => theme.space[1]};
  }
`;
