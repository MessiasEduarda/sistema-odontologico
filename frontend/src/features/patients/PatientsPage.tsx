"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { UserPlus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import styled from "styled-components";
import { Button } from "@/components/ui/Button";
import { DataTable, Pagination } from "@/components/ui/DataTable";
import { Badge, Muted, PageHeader, Stack } from "@/components/ui/display";
import { SelectField } from "@/components/ui/fields";
import { Modal } from "@/components/ui/Modal";
import { SearchInput } from "@/components/ui/SearchInput";
import { api } from "@/lib/api/endpoints";
import type { PatientSummary } from "@/lib/api/types";
import { ageFrom, formatCpf, formatDate, formatPhone } from "@/lib/format";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { PatientForm } from "./PatientForm";

export function PatientsPage() {
  const router = useRouter();
  const can = useCan();
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("true");
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);

  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["patients", "list", q, status, page],
    queryFn: () => api.patients.search({ q, active: status === "" ? undefined : status === "true", page, size: 20 }),
    placeholderData: keepPreviousData,
  });

  const create = useAction(api.patients.create, {
    success: "Paciente cadastrado.",
    invalidate: [["patients"]],
    onSuccess: (p) => router.push(`/pacientes/${p.id}`),
  });

  return (
    <Stack>
      <PageHeader
        title="Pacientes"
        description={data ? `${data.totalItems} paciente(s)` : undefined}
        actions={
          can(P.PATIENTS_WRITE) && (
            <Button onClick={() => setCreating(true)}>
              <UserPlus size={18} aria-hidden /> Novo paciente
            </Button>
          )
        }
      />
      <FilterBar>
        <SearchInput
          label="Buscar paciente"
          placeholder="Buscar por nome, CPF, telefone ou e-mail…"
          value={q}
          onChange={(value) => {
            setQ(value);
            setPage(0);
          }}
        />
        <StatusFilter>
          <SelectField
            label="Situação"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
            options={[
              { value: "true", label: "Ativos" },
              { value: "false", label: "Inativos" },
              { value: "", label: "Todos" },
            ]}
          />
        </StatusFilter>
      </FilterBar>
      <DataTable<PatientSummary>
        caption="Pacientes"
        rows={data?.items}
        loading={isPending}
        error={error}
        onRetry={refetch}
        rowKey={(p) => p.id}
        onRowClick={(p) => router.push(`/pacientes/${p.id}`)}
        emptyTitle={q ? "Nenhum paciente encontrado" : "Nenhum paciente cadastrado"}
        emptyDescription={q ? "Tente outro termo de busca." : "Cadastre o primeiro paciente da clínica."}
        columns={[
          {
            key: "name",
            header: "Paciente",
            minWidth: "220px",
            render: (p) => (
              <div>
                <strong>{p.socialName ?? p.fullName}</strong>
                {p.socialName && <Muted> ({p.fullName})</Muted>}
                {!p.active && (
                  <>
                    {" "}
                    <Badge>Inativo</Badge>
                  </>
                )}
              </div>
            ),
          },
          {
            key: "age",
            header: "Idade",
            hideOnMobile: true,
            render: (p) => (p.birthDate ? `${ageFrom(p.birthDate)} anos` : "—"),
          },
          {
            key: "cpf",
            header: "CPF",
            hideOnMobile: true,
            minWidth: "140px",
            render: (p) => (p.cpf ? formatCpf(p.cpf) : "—"),
          },
          { key: "mobile", header: "Celular", minWidth: "140px", render: (p) => formatPhone(p.mobile ?? p.phone) },
          {
            key: "next",
            header: "Próxima consulta",
            render: (p) => (p.nextAppointment ? formatDate(p.nextAppointment) : "—"),
          },
          {
            key: "last",
            header: "Última consulta",
            hideOnMobile: true,
            render: (p) => (p.lastAppointment ? formatDate(p.lastAppointment) : "—"),
          },
        ]}
      />
      {data && (
        <Pagination page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} onChange={setPage} />
      )}

      <Modal open={creating} onClose={() => setCreating(false)} title="Novo paciente" size="lg">
        {creating && (
          <PatientForm
            saving={create.isPending}
            serverError={create.error}
            submitLabel="Cadastrar paciente"
            onSubmit={(input) => create.mutate(input)}
            onCancel={() => setCreating(false)}
          />
        )}
      </Modal>
    </Stack>
  );
}

const FilterBar = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: ${({ theme }) => theme.space[3]};
`;

const StatusFilter = styled.div`
  width: 180px;
`;
