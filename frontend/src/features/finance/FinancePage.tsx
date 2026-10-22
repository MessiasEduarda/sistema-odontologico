"use client";

import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, ArrowDownCircle, ArrowUpCircle, CalendarClock, Plus, Wallet } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import styled from "styled-components";
import { PatientSelect } from "@/components/domain/PatientSelect";
import { ReasonDialog } from "@/components/domain/ReasonDialog";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import {
  Badge,
  Card,
  CardTitle,
  FilterBar,
  KpiCard,
  KpiGrid,
  Muted,
  PageHeader,
  Row,
  Stack,
} from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow, SelectField, TextAreaField } from "@/components/ui/fields";
import { LoadingState } from "@/components/ui/LoadingState";
import { Modal } from "@/components/ui/Modal";
import { SearchInput, useRowSearch } from "@/components/ui/SearchInput";
import { Tabs } from "@/components/ui/Tabs";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { InstallmentRow, Payable, PaymentMethod, ReceivableSummary, Supplier } from "@/lib/api/types";
import { formatDate, formatMoney, toDateInput } from "@/lib/format";
import { FINANCE_STATUS, methodLabel, PAYMENT_METHODS } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { PayDialog, ReceivableDetail } from "./ReceivableDetail";

const INV = [["receivables"], ["installments"], ["finance-summary"], ["cash-flow"], ["payables"], ["dashboard"]];

export function FinancePage() {
  const params = useSearchParams();
  const [tab, setTab] = useState(params.get("aba") ?? "resumo");
  const [openReceivable, setOpenReceivable] = useState<string | null>(params.get("conta"));

  return (
    <Stack>
      <PageHeader title="Financeiro" />
      <Tabs
        label="Áreas do financeiro"
        active={tab}
        onChange={setTab}
        items={[
          { id: "resumo", label: "Resumo e caixa", content: <SummaryTab /> },
          {
            id: "receber",
            label: "A receber",
            content: <InstallmentsTab initialFilter={params.get("filtro") ?? "OPEN"} onOpen={setOpenReceivable} />,
          },
          { id: "contas", label: "Contas de pacientes", content: <ReceivablesTab onOpen={setOpenReceivable} /> },
          { id: "pagar", label: "A pagar", content: <PayablesTab /> },
          { id: "fornecedores", label: "Fornecedores", content: <SuppliersTab /> },
        ]}
      />
      <ReceivableDetail receivableId={openReceivable} onClose={() => setOpenReceivable(null)} />
    </Stack>
  );
}

/* ------------------------------------------------------------------ Resumo e fluxo de caixa */

function SummaryTab() {
  const today = new Date();
  const [from, setFrom] = useState(toDateInput(new Date(today.getFullYear(), today.getMonth(), 1)));
  const [to, setTo] = useState(toDateInput(today));
  const summary = useQuery({ queryKey: ["finance-summary"], queryFn: api.finance.summary });
  const flow = useQuery({
    queryKey: ["cash-flow", from, to],
    queryFn: () => api.finance.cashFlow(from, to),
    enabled: Boolean(from && to),
  });

  if (summary.isPending) return <LoadingState />;
  const s = summary.data;
  const days = flow.data?.days ?? [];
  const max = Math.max(1, ...days.map((d) => Math.max(Number(d.inflow), Number(d.outflow))));

  return (
    <Stack $gap={5}>
      {s && (
        <KpiGrid>
          <KpiCard
            icon={ArrowDownCircle}
            label="Recebido no mês"
            value={formatMoney(s.receivedThisMonth)}
            tone="success"
          />
          <KpiCard icon={ArrowUpCircle} label="Pago no mês" value={formatMoney(s.paidOutThisMonth)} />
          <KpiCard icon={Wallet} label="A receber (em aberto)" value={formatMoney(s.openReceivables)} />
          <KpiCard
            icon={AlertTriangle}
            label="Inadimplência"
            value={formatMoney(s.overdueReceivables)}
            hint={`${s.overdueInstallments} parcela(s) em atraso`}
            tone={Number(s.overdueReceivables) > 0 ? "danger" : "neutral"}
          />
          <KpiCard
            icon={CalendarClock}
            label="A pagar em 7 dias"
            value={formatMoney(s.payablesDueNext7Days)}
            hint={`Total em aberto: ${formatMoney(s.openPayables)}`}
          />
        </KpiGrid>
      )}
      <Card>
        <CardTitle>Fluxo de caixa</CardTitle>
        <Row $gap={3}>
          <TextField label="De" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          <TextField label="Até" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </Row>
        {flow.error && <Alert tone="danger">{flow.error.message}</Alert>}
        {flow.data && (
          <Stack $gap={4}>
            <Totals>
              <span>
                Entradas: <strong>{formatMoney(flow.data.totalInflow)}</strong>
              </span>
              <span>
                Saídas: <strong>{formatMoney(flow.data.totalOutflow)}</strong>
              </span>
              <span>
                Saldo do período: <strong>{formatMoney(flow.data.net)}</strong>
              </span>
            </Totals>
            <Chart role="img" aria-label="Gráfico de entradas e saídas por dia">
              {days.map((d) => (
                <Bar
                  key={d.date}
                  title={`${formatDate(d.date)}: entradas ${formatMoney(d.inflow)}, saídas ${formatMoney(d.outflow)}`}
                >
                  <In style={{ height: `${(Number(d.inflow) / max) * 100}%` }} />
                  <Out style={{ height: `${(Number(d.outflow) / max) * 100}%` }} />
                </Bar>
              ))}
            </Chart>
            <Legend>
              <span>
                <i style={{ background: "#2E7D4F" }} /> Entradas
              </span>
              <span>
                <i style={{ background: "#B3261E" }} /> Saídas (pagamentos e estornos)
              </span>
            </Legend>
            {flow.data.byMethod.length > 0 && (
              <div>
                <Muted>Recebimentos por forma de pagamento:</Muted>
                <Row $gap={2}>
                  {flow.data.byMethod.map((m) => (
                    <Badge key={m.method} $tone="primary">
                      {methodLabel(m.method)}: {formatMoney(m.amount)}
                    </Badge>
                  ))}
                </Row>
              </div>
            )}
          </Stack>
        )}
      </Card>
    </Stack>
  );
}

/* ------------------------------------------------------------------ Parcelas a receber */

function InstallmentsTab({ initialFilter, onOpen }: { initialFilter: string; onOpen: (id: string) => void }) {
  const can = useCan();
  const [filter, setFilter] = useState(initialFilter);
  const [paying, setPaying] = useState<InstallmentRow | null>(null);
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["installments", filter],
    queryFn: () => api.finance.installments({ status: filter }),
  });
  const search = useRowSearch(data, (i) => [i.patientName]);
  const total = (search.rows ?? []).reduce((s, i) => s + Number(i.balance), 0);
  return (
    <Stack $gap={4}>
      <Row $justify="space-between">
        <FilterBar>
          <Filter>
            <SelectField
              label="Mostrar"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              options={[
                { value: "OPEN", label: "Em aberto" },
                { value: "OVERDUE", label: "Em atraso" },
                { value: "PAID", label: "Pagas" },
                { value: "ALL", label: "Todas" },
              ]}
            />
          </Filter>
          <SearchInput
            label="Buscar por nome"
            placeholder="Nome do paciente…"
            value={search.query}
            onChange={search.setQuery}
          />
        </FilterBar>
        {filter !== "PAID" && <Muted>Saldo listado: {formatMoney(total)}</Muted>}
      </Row>
      <DataTable<InstallmentRow>
        caption="Parcelas"
        rows={search.rows}
        loading={isPending}
        error={error}
        onRetry={refetch}
        rowKey={(i) => i.id}
        onRowClick={(i) => onOpen(i.receivableId)}
        emptyTitle="Nenhuma parcela"
        columns={[
          { key: "due", header: "Vencimento", minWidth: "110px", render: (i) => formatDate(i.dueDate) },
          { key: "patient", header: "Paciente", render: (i) => <strong>{i.patientName}</strong> },
          {
            key: "desc",
            header: "Descrição",
            hideOnMobile: true,
            render: (i) => `${i.description} (${i.number}/${i.installmentsCount})`,
          },
          { key: "amount", header: "Valor", align: "right", render: (i) => formatMoney(i.amount) },
          { key: "balance", header: "Saldo", align: "right", render: (i) => formatMoney(i.balance) },
          {
            key: "status",
            header: "Situação",
            render: (i) =>
              i.overdue ? (
                <Badge $tone="danger">Em atraso</Badge>
              ) : (
                <Badge $tone={FINANCE_STATUS[i.status]?.tone}>{FINANCE_STATUS[i.status]?.label}</Badge>
              ),
          },
          {
            key: "pay",
            header: "",
            render: (i) =>
              can(P.FINANCE_WRITE) &&
              i.status === "OPEN" && (
                <Button
                  variant="outline"
                  onClick={(e) => {
                    e.stopPropagation();
                    setPaying(i);
                  }}
                >
                  Receber
                </Button>
              ),
          },
        ]}
      />
      {paying && <PayDialog installment={paying} onClose={() => setPaying(null)} />}
    </Stack>
  );
}

/* ------------------------------------------------------------------ Contas a receber (por paciente) */

function ReceivablesTab({ onOpen }: { onOpen: (id: string) => void }) {
  const can = useCan();
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("OPEN");
  const [creating, setCreating] = useState(false);
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["receivables", "list", q, status],
    queryFn: () => api.finance.receivables({ q, status: status || undefined }),
  });
  return (
    <Stack $gap={4}>
      <Bar2>
        <SearchInput label="Buscar conta" placeholder="Paciente ou descrição…" value={q} onChange={setQ} />
        <Filter>
          <SelectField
            label="Situação"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            placeholder="Todas"
            options={Object.entries(FINANCE_STATUS).map(([value, s]) => ({ value, label: s.label }))}
          />
        </Filter>
        {can(P.FINANCE_WRITE) && (
          <Button onClick={() => setCreating(true)}>
            <Plus size={16} aria-hidden /> Conta avulsa
          </Button>
        )}
      </Bar2>
      <DataTable<ReceivableSummary>
        caption="Contas a receber"
        rows={data}
        loading={isPending}
        error={error}
        onRetry={refetch}
        rowKey={(r) => r.id}
        onRowClick={(r) => onOpen(r.id)}
        emptyTitle="Nenhuma conta"
        columns={[
          { key: "date", header: "Criada em", hideOnMobile: true, render: (r) => formatDate(r.createdAt) },
          { key: "patient", header: "Paciente", render: (r) => <strong>{r.patientName}</strong> },
          { key: "desc", header: "Descrição", hideOnMobile: true, render: (r) => r.description },
          { key: "total", header: "Total", align: "right", render: (r) => formatMoney(r.total) },
          { key: "balance", header: "Saldo", align: "right", render: (r) => formatMoney(r.balance) },
          { key: "next", header: "Próx. vencimento", render: (r) => (r.nextDueDate ? formatDate(r.nextDueDate) : "—") },
          {
            key: "status",
            header: "Situação",
            render: (r) =>
              r.overdueInstallments > 0 ? (
                <Badge $tone="danger">{r.overdueInstallments} em atraso</Badge>
              ) : (
                <Badge $tone={FINANCE_STATUS[r.status]?.tone}>{FINANCE_STATUS[r.status]?.label}</Badge>
              ),
          },
        ]}
      />
      {creating && <NewReceivable onClose={() => setCreating(false)} onCreated={onOpen} />}
    </Stack>
  );
}

function NewReceivable({ onClose, onCreated }: { onClose: () => void; onCreated: (id: string) => void }) {
  const [patient, setPatient] = useState<{ id: string; name: string } | null>(null);
  const [description, setDescription] = useState("");
  const [total, setTotal] = useState(0);
  const [installments, setInstallments] = useState(1);
  const [firstDue, setFirstDue] = useState(toDateInput(new Date()));
  const create = useAction(
    () =>
      api.finance.createReceivable({
        patientId: patient?.id ?? "",
        description,
        total,
        installments,
        firstDueDate: firstDue,
      }),
    {
      success: "Conta criada com sucesso!",
      invalidate: INV,
      onSuccess: (r) => {
        onClose();
        onCreated(r.id);
      },
    },
  );
  return (
    <Modal
      open
      onClose={onClose}
      title="Nova conta a receber"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => create.mutate(undefined)}
            loading={create.isPending}
            disabled={!patient || !description.trim() || !(total > 0)}
          >
            Criar
          </Button>
        </>
      }
    >
      <FormGrid $columns={2}>
        <FullRow>
          <PatientSelect value={patient} onChange={setPatient} required />
        </FullRow>
        <FullRow>
          <TextField
            label="Descrição"
            required
            maxLength={200}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </FullRow>
        <TextField
          label="Valor total (R$)"
          type="number"
          step="0.01"
          min={0.01}
          value={total}
          onChange={(e) => setTotal(Number(e.target.value))}
        />
        <TextField
          label="Parcelas"
          type="number"
          min={1}
          max={48}
          value={installments}
          onChange={(e) => setInstallments(Math.min(48, Math.max(1, Number(e.target.value))))}
        />
        <TextField label="1º vencimento" type="date" value={firstDue} onChange={(e) => setFirstDue(e.target.value)} />
        {create.error && (
          <FullRow>
            <Alert tone="danger">{create.error.message}</Alert>
          </FullRow>
        )}
      </FormGrid>
    </Modal>
  );
}

/* ------------------------------------------------------------------ Contas a pagar */

const PAYABLE_CATEGORIES = [
  "MATERIAIS",
  "ALUGUEL",
  "SALARIOS",
  "IMPOSTOS",
  "SERVICOS",
  "LABORATORIO",
  "EQUIPAMENTOS",
  "OUTROS",
].map((c) => ({ value: c, label: c.charAt(0) + c.slice(1).toLowerCase() }));

function PayablesTab() {
  const can = useCan();
  const [status, setStatus] = useState("OPEN");
  const [editing, setEditing] = useState<Payable | "new" | null>(null);
  const [paying, setPaying] = useState<Payable | null>(null);
  const [cancelling, setCancelling] = useState<Payable | null>(null);
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["payables", status],
    queryFn: () => api.finance.payables({ status: status || undefined }),
  });
  const search = useRowSearch(data, (p) => [p.supplierName, p.description]);
  const cancel = useAction((reason: string) => api.finance.cancelPayable(cancelling?.id ?? "", reason), {
    success: "Conta cancelada com sucesso!",
    invalidate: INV,
    onSuccess: () => setCancelling(null),
  });
  const writable = can(P.FINANCE_WRITE);
  return (
    <Stack $gap={4}>
      <Bar2>
        <FilterBar>
          <Filter>
            <SelectField
              label="Situação"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              placeholder="Todas"
              options={Object.entries(FINANCE_STATUS).map(([value, s]) => ({ value, label: s.label }))}
            />
          </Filter>
          <SearchInput
            label="Buscar por nome"
            placeholder="Fornecedor ou descrição…"
            value={search.query}
            onChange={search.setQuery}
          />
        </FilterBar>
        {writable && (
          <Button onClick={() => setEditing("new")}>
            <Plus size={16} aria-hidden /> Nova conta a pagar
          </Button>
        )}
      </Bar2>
      <DataTable<Payable>
        caption="Contas a pagar"
        rows={search.rows}
        loading={isPending}
        error={error}
        onRetry={refetch}
        rowKey={(p) => p.id}
        emptyTitle="Nenhuma conta a pagar"
        columns={[
          { key: "due", header: "Vencimento", render: (p) => formatDate(p.dueDate) },
          { key: "desc", header: "Descrição", render: (p) => <strong>{p.description}</strong> },
          { key: "supplier", header: "Fornecedor", hideOnMobile: true, render: (p) => p.supplierName ?? "—" },
          { key: "cat", header: "Categoria", hideOnMobile: true, render: (p) => p.category },
          { key: "amount", header: "Valor", align: "right", render: (p) => formatMoney(p.amount) },
          {
            key: "status",
            header: "Situação",
            render: (p) =>
              p.overdue ? (
                <Badge $tone="danger">Vencida</Badge>
              ) : (
                <Badge $tone={FINANCE_STATUS[p.status]?.tone}>
                  {FINANCE_STATUS[p.status]?.label}
                  {p.paidAt ? ` em ${formatDate(p.paidAt)}` : ""}
                </Badge>
              ),
          },
          {
            key: "actions",
            header: "",
            render: (p) =>
              writable &&
              p.status === "OPEN" && (
                <Row $gap={2} $wrap={false}>
                  <Button variant="outline" onClick={() => setPaying(p)}>
                    Pagar
                  </Button>
                  <Button variant="ghost" onClick={() => setEditing(p)}>
                    Editar
                  </Button>
                  <Button variant="ghost" onClick={() => setCancelling(p)}>
                    Cancelar
                  </Button>
                </Row>
              ),
          },
        ]}
      />
      {editing && <PayableForm payable={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
      {paying && <PayPayable payable={paying} onClose={() => setPaying(null)} />}
      <ReasonDialog
        open={Boolean(cancelling)}
        title="Cancelar conta a pagar"
        confirmLabel="Cancelar conta"
        loading={cancel.isPending}
        onConfirm={(r) => cancel.mutate(r)}
        onClose={() => setCancelling(null)}
      />
    </Stack>
  );
}

function PayableForm({ payable, onClose }: { payable: Payable | null; onClose: () => void }) {
  const suppliers = useQuery({ queryKey: ["suppliers"], queryFn: api.finance.suppliers });
  const [v, setV] = useState({
    supplierId: payable?.supplierId ?? "",
    description: payable?.description ?? "",
    category: payable?.category ?? "MATERIAIS",
    amount: Number(payable?.amount ?? 0),
    dueDate: payable?.dueDate ?? toDateInput(new Date()),
    notes: payable?.notes ?? "",
  });
  const save = useAction(
    () => {
      const body = { ...v, supplierId: v.supplierId || null, notes: v.notes || null };
      return payable ? api.finance.updatePayable(payable.id, body) : api.finance.createPayable(body);
    },
    { success: "Conta salva com sucesso!", invalidate: INV, onSuccess: onClose },
  );
  return (
    <Modal
      open
      onClose={onClose}
      title={payable ? "Editar conta a pagar" : "Nova conta a pagar"}
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => save.mutate(undefined)}
            loading={save.isPending}
            disabled={!v.description.trim() || !(v.amount > 0)}
          >
            Salvar
          </Button>
        </>
      }
    >
      <FormGrid $columns={2}>
        <FullRow>
          <TextField
            label="Descrição"
            required
            maxLength={200}
            value={v.description}
            onChange={(e) => setV({ ...v, description: e.target.value })}
          />
        </FullRow>
        <SelectField
          label="Fornecedor"
          placeholder="(nenhum)"
          value={v.supplierId}
          onChange={(e) => setV({ ...v, supplierId: e.target.value })}
          options={(suppliers.data ?? []).filter((s) => s.active).map((s) => ({ value: s.id, label: s.name }))}
        />
        <SelectField
          label="Categoria"
          value={v.category}
          onChange={(e) => setV({ ...v, category: e.target.value })}
          options={PAYABLE_CATEGORIES}
        />
        <TextField
          label="Valor (R$)"
          type="number"
          step="0.01"
          min={0.01}
          value={v.amount}
          onChange={(e) => setV({ ...v, amount: Number(e.target.value) })}
        />
        <TextField
          label="Vencimento"
          type="date"
          value={v.dueDate}
          onChange={(e) => setV({ ...v, dueDate: e.target.value })}
        />
        <FullRow>
          <TextAreaField
            label="Observações"
            rows={2}
            maxLength={500}
            value={v.notes}
            onChange={(e) => setV({ ...v, notes: e.target.value })}
          />
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

function PayPayable({ payable, onClose }: { payable: Payable; onClose: () => void }) {
  const [method, setMethod] = useState<PaymentMethod>("PIX");
  const pay = useAction(() => api.finance.payPayable(payable.id, method), {
    success: "Pagamento registrado com sucesso!",
    invalidate: INV,
    onSuccess: onClose,
  });
  return (
    <Modal
      open
      onClose={onClose}
      title="Registrar pagamento"
      size="sm"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => pay.mutate(undefined)} loading={pay.isPending}>
            Confirmar pagamento
          </Button>
        </>
      }
    >
      <Stack $gap={3}>
        <p>
          {payable.description} · <strong>{formatMoney(payable.amount)}</strong>
        </p>
        <SelectField
          label="Forma de pagamento"
          value={method}
          onChange={(e) => setMethod(e.target.value as PaymentMethod)}
          options={PAYMENT_METHODS}
        />
        {pay.error && <Alert tone="danger">{pay.error.message}</Alert>}
      </Stack>
    </Modal>
  );
}

/* ------------------------------------------------------------------ Fornecedores */

export function SuppliersTab() {
  const can = useCan();
  const [editing, setEditing] = useState<Supplier | "new" | null>(null);
  const { data, isPending, error, refetch } = useQuery({ queryKey: ["suppliers"], queryFn: api.finance.suppliers });
  const search = useRowSearch(data, (s) => [s.name]);
  return (
    <Stack $gap={4}>
      <Row $justify="space-between">
        <SearchInput
          label="Buscar por nome"
          placeholder="Nome do fornecedor…"
          value={search.query}
          onChange={search.setQuery}
        />
        {can(P.FINANCE_WRITE, P.INVENTORY_WRITE) && (
          <Button onClick={() => setEditing("new")}>
            <Plus size={16} aria-hidden /> Novo fornecedor
          </Button>
        )}
      </Row>
      <DataTable<Supplier>
        caption="Fornecedores"
        rows={search.rows}
        loading={isPending}
        error={error}
        onRetry={refetch}
        rowKey={(s) => s.id}
        onRowClick={can(P.FINANCE_WRITE, P.INVENTORY_WRITE) ? setEditing : undefined}
        emptyTitle="Nenhum fornecedor"
        columns={[
          { key: "name", header: "Nome", render: (s) => <strong>{s.name}</strong> },
          { key: "doc", header: "CNPJ/CPF", hideOnMobile: true, render: (s) => s.document ?? "—" },
          { key: "phone", header: "Telefone", render: (s) => s.phone ?? "—" },
          { key: "email", header: "E-mail", hideOnMobile: true, render: (s) => s.email ?? "—" },
          {
            key: "status",
            header: "Situação",
            render: (s) => (s.active ? <Badge $tone="success">Ativo</Badge> : <Badge>Inativo</Badge>),
          },
        ]}
      />
      {editing && <SupplierForm supplier={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </Stack>
  );
}

function SupplierForm({ supplier, onClose }: { supplier: Supplier | null; onClose: () => void }) {
  const [v, setV] = useState({
    name: supplier?.name ?? "",
    document: supplier?.document ?? "",
    phone: supplier?.phone ?? "",
    email: supplier?.email ?? "",
    notes: supplier?.notes ?? "",
    active: supplier?.active ?? true,
  });
  const save = useAction(
    () => {
      const body = {
        ...v,
        document: v.document || null,
        phone: v.phone || null,
        email: v.email || null,
        notes: v.notes || null,
      };
      return supplier ? api.finance.updateSupplier(supplier.id, body) : api.finance.createSupplier(body);
    },
    { success: "Fornecedor salvo com sucesso!", invalidate: [["suppliers"]], onSuccess: onClose },
  );
  return (
    <Modal
      open
      onClose={onClose}
      title={supplier ? "Editar fornecedor" : "Novo fornecedor"}
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => save.mutate(undefined)} loading={save.isPending} disabled={!v.name.trim()}>
            Salvar
          </Button>
        </>
      }
    >
      <FormGrid $columns={2}>
        <FullRow>
          <TextField
            label="Nome"
            required
            maxLength={160}
            value={v.name}
            onChange={(e) => setV({ ...v, name: e.target.value })}
          />
        </FullRow>
        <TextField
          label="CNPJ/CPF"
          maxLength={18}
          value={v.document}
          onChange={(e) => setV({ ...v, document: e.target.value })}
        />
        <TextField
          label="Telefone"
          maxLength={20}
          value={v.phone}
          onChange={(e) => setV({ ...v, phone: e.target.value })}
        />
        <FullRow>
          <TextField
            label="E-mail"
            type="email"
            maxLength={254}
            value={v.email}
            onChange={(e) => setV({ ...v, email: e.target.value })}
          />
        </FullRow>
        <FullRow>
          <TextAreaField
            label="Observações"
            rows={2}
            maxLength={500}
            value={v.notes}
            onChange={(e) => setV({ ...v, notes: e.target.value })}
          />
        </FullRow>
        <CheckboxField label="Ativo" checked={v.active} onChange={(e) => setV({ ...v, active: e.target.checked })} />
        {save.error && (
          <FullRow>
            <Alert tone="danger">{save.error.message}</Alert>
          </FullRow>
        )}
      </FormGrid>
    </Modal>
  );
}

/* ------------------------------------------------------------------ Estilos */

const Filter = styled.div`
  width: 200px;
`;

const Bar2 = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: ${({ theme }) => theme.space[3]};
`;

const Totals = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[6]};
  margin-top: ${({ theme }) => theme.space[4]};
`;

const Chart = styled.div`
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 180px;
  padding: ${({ theme }) => theme.space[2]} 0;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  overflow-x: auto;
`;

const Bar = styled.div`
  flex: 1;
  min-width: 8px;
  height: 100%;
  display: flex;
  align-items: flex-end;
  gap: 1px;
`;

const In = styled.span`
  flex: 1;
  background: #2e7d4f;
  border-radius: 2px 2px 0 0;
`;

const Out = styled.span`
  flex: 1;
  background: #b3261e;
  border-radius: 2px 2px 0 0;
`;

const Legend = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.space[4]};
  font-size: ${({ theme }) => theme.fontSizes.sm};
  color: ${({ theme }) => theme.colors.textMuted};

  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
  i {
    width: 12px;
    height: 12px;
    border-radius: 3px;
    display: inline-block;
  }
`;
