"use client";

import { useQuery } from "@tanstack/react-query";
import { ClipboardPlus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import styled from "styled-components";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, PageHeader, Stack } from "@/components/ui/display";
import { SelectField } from "@/components/ui/fields";
import { api } from "@/lib/api/endpoints";
import type { PlanSummary } from "@/lib/api/types";
import { formatDate, formatMoney } from "@/lib/format";
import { PLAN_STATUS } from "@/lib/labels";
import { useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";

export function PlansPage() {
  const router = useRouter();
  const can = useCan();
  const [status, setStatus] = useState("");
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["plans", "list", status],
    queryFn: () => api.plans.list({ status: status || undefined }),
  });

  return (
    <Stack>
      <PageHeader
        title="Tratamentos e orçamentos"
        actions={
          can(P.TREATMENTS_WRITE) && (
            <Button onClick={() => router.push("/tratamentos/novo")}>
              <ClipboardPlus size={18} aria-hidden /> Novo orçamento
            </Button>
          )
        }
      />
      <Filter>
        <SelectField
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          placeholder="Todos"
          options={Object.entries(PLAN_STATUS).map(([value, s]) => ({ value, label: s.label }))}
        />
      </Filter>
      <DataTable<PlanSummary>
        caption="Orçamentos"
        rows={data}
        loading={isPending}
        error={error}
        onRetry={refetch}
        rowKey={(p) => p.id}
        onRowClick={(p) => router.push(`/tratamentos/${p.id}`)}
        emptyTitle="Nenhum orçamento"
        columns={[
          { key: "date", header: "Criado em", minWidth: "110px", render: (p) => formatDate(p.createdAt) },
          { key: "patient", header: "Paciente", render: (p) => <strong>{p.patientName}</strong> },
          { key: "title", header: "Título", render: (p) => p.title },
          { key: "prof", header: "Profissional", hideOnMobile: true, render: (p) => p.professionalName },
          { key: "total", header: "Total", align: "right", render: (p) => formatMoney(p.total) },
          {
            key: "progress",
            header: "Realizado",
            hideOnMobile: true,
            render: (p) => (p.itemsTotal ? `${p.itemsDone}/${p.itemsTotal}` : "—"),
          },
          {
            key: "status",
            header: "Status",
            render: (p) => <Badge $tone={PLAN_STATUS[p.status]?.tone}>{PLAN_STATUS[p.status]?.label}</Badge>,
          },
        ]}
      />
    </Stack>
  );
}

const Filter = styled.div`
  width: 220px;
`;
