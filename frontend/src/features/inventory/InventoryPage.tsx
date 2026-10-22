"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowDownUp, History, Plus } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable, RowActions } from "@/components/ui/DataTable";
import { Badge, Muted, PageHeader, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow, SelectField } from "@/components/ui/fields";
import { IconButton } from "@/components/ui/IconButton";
import { Modal } from "@/components/ui/Modal";
import { SearchInput, useRowSearch } from "@/components/ui/SearchInput";
import { Tabs } from "@/components/ui/Tabs";
import { TextField } from "@/components/ui/TextField";
import { SuppliersTab } from "@/features/finance/FinancePage";
import { api } from "@/lib/api/endpoints";
import type { Product, StockMovement } from "@/lib/api/types";
import { formatDate, formatDateTime, formatMoney } from "@/lib/format";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";

const INV = [["products"], ["inventory-alerts"], ["dashboard"]];
const qty = (n: number | string) => Number(n).toLocaleString("pt-BR", { maximumFractionDigits: 3 });

export function InventoryPage() {
  const params = useSearchParams();
  const [tab, setTab] = useState("produtos");
  return (
    <Stack>
      <PageHeader title="Estoque" />
      <Tabs
        label="Áreas do estoque"
        active={tab}
        onChange={setTab}
        items={[
          { id: "produtos", label: "Produtos", content: <ProductsTab initialLow={params.get("baixo") === "1"} /> },
          { id: "alertas", label: "Alertas", content: <AlertsTab /> },
          { id: "fornecedores", label: "Fornecedores", content: <SuppliersTab /> },
        ]}
      />
    </Stack>
  );
}

function ProductsTab({ initialLow }: { initialLow: boolean }) {
  const can = useCan();
  const writable = can(P.INVENTORY_WRITE);
  const [q, setQ] = useState("");
  const [low, setLow] = useState(initialLow);
  const [editing, setEditing] = useState<Product | "new" | null>(null);
  const [moving, setMoving] = useState<Product | null>(null);
  const [history, setHistory] = useState<Product | null>(null);
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["products", q, low],
    queryFn: () => api.inventory.products(q, low || undefined),
  });

  return (
    <Stack $gap={4}>
      <Bar>
        <SearchInput label="Buscar produto" placeholder="Nome ou categoria…" value={q} onChange={setQ} />
        <CheckboxField label="Somente estoque baixo" checked={low} onChange={(e) => setLow(e.target.checked)} />
        {writable && (
          <Button onClick={() => setEditing("new")}>
            <Plus size={16} aria-hidden /> Novo produto
          </Button>
        )}
      </Bar>
      <DataTable<Product>
        caption="Produtos"
        rows={data}
        loading={isPending}
        error={error}
        onRetry={refetch}
        rowKey={(p) => p.id}
        emptyTitle="Nenhum produto"
        columns={[
          {
            key: "name",
            header: "Produto",
            render: (p) => (
              <span>
                <strong>{p.name}</strong> {!p.active && <Badge>Inativo</Badge>}
              </span>
            ),
          },
          { key: "cat", header: "Categoria", hideOnMobile: true, render: (p) => p.category ?? "—" },
          {
            key: "stock",
            header: "Saldo",
            align: "right",
            render: (p) => (
              <StockValue $low={p.lowStock}>
                {qty(p.currentStock)} <small>{p.unit}</small>
              </StockValue>
            ),
          },
          {
            key: "min",
            header: "Mínimo",
            align: "right",
            hideOnMobile: true,
            render: (p) => `${qty(p.minStock)} ${p.unit}`,
          },
          {
            key: "status",
            header: "Situação",
            render: (p) =>
              p.lowStock ? <Badge $tone="danger">Estoque baixo</Badge> : <Badge $tone="success">OK</Badge>,
          },
          { key: "supplier", header: "Fornecedor", hideOnMobile: true, render: (p) => p.supplierName ?? "—" },
          {
            key: "actions",
            header: "Ações",
            render: (p) => (
              <RowActions>
                <IconButton icon={History} label={`Movimentações de ${p.name}`} onClick={() => setHistory(p)} />
                {writable && (
                  <IconButton icon={ArrowDownUp} label={`Movimentar ${p.name}`} onClick={() => setMoving(p)} />
                )}
                {writable && (
                  <Button variant="ghost" onClick={() => setEditing(p)}>
                    Editar
                  </Button>
                )}
              </RowActions>
            ),
          },
        ]}
      />
      {editing && <ProductForm product={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
      {moving && <MovementForm product={moving} onClose={() => setMoving(null)} />}
      {history && <MovementsHistory product={history} onClose={() => setHistory(null)} />}
    </Stack>
  );
}

function ProductForm({ product, onClose }: { product: Product | null; onClose: () => void }) {
  const suppliers = useQuery({ queryKey: ["suppliers"], queryFn: api.finance.suppliers });
  const [v, setV] = useState({
    name: product?.name ?? "",
    category: product?.category ?? "",
    unit: product?.unit ?? "un",
    minStock: Number(product?.minStock ?? 0),
    supplierId: product?.supplierId ?? "",
    active: product?.active ?? true,
  });
  const save = useAction(
    () => {
      const body = { ...v, category: v.category || null, supplierId: v.supplierId || null };
      return product ? api.inventory.update(product.id, body) : api.inventory.create(body);
    },
    { success: "Produto salvo com sucesso!", invalidate: INV, onSuccess: onClose },
  );
  return (
    <Modal
      open
      onClose={onClose}
      title={product ? "Editar produto" : "Novo produto"}
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => save.mutate(undefined)}
            loading={save.isPending}
            disabled={!v.name.trim() || !v.unit.trim()}
          >
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
          label="Categoria"
          maxLength={60}
          value={v.category}
          onChange={(e) => setV({ ...v, category: e.target.value })}
        />
        <TextField
          label="Unidade"
          required
          maxLength={20}
          placeholder="un, cx, pct, ml…"
          value={v.unit}
          onChange={(e) => setV({ ...v, unit: e.target.value })}
        />
        <TextField
          label="Estoque mínimo"
          type="number"
          min={0}
          step="0.001"
          value={v.minStock}
          onChange={(e) => setV({ ...v, minStock: Number(e.target.value) })}
        />
        <SelectField
          label="Fornecedor"
          placeholder="(nenhum)"
          value={v.supplierId}
          onChange={(e) => setV({ ...v, supplierId: e.target.value })}
          options={(suppliers.data ?? []).filter((s) => s.active).map((s) => ({ value: s.id, label: s.name }))}
        />
        <CheckboxField label="Ativo" checked={v.active} onChange={(e) => setV({ ...v, active: e.target.checked })} />
        {!product && (
          <FullRow>
            <Muted>O saldo começa em zero; registre a entrada inicial em “Movimentar”.</Muted>
          </FullRow>
        )}
        {save.error && (
          <FullRow>
            <Alert tone="danger">{save.error.message}</Alert>
          </FullRow>
        )}
      </FormGrid>
    </Modal>
  );
}

function MovementForm({ product, onClose }: { product: Product; onClose: () => void }) {
  const [v, setV] = useState({ type: "IN", quantity: 0, unitCost: "", lotNumber: "", expiryDate: "", reason: "" });
  const save = useAction(
    () =>
      api.inventory.move(product.id, {
        type: v.type,
        quantity: v.quantity,
        unitCost: v.unitCost ? Number(v.unitCost) : null,
        lotNumber: v.lotNumber || undefined,
        expiryDate: v.expiryDate || undefined,
        reason: v.reason || undefined,
      }),
    {
      success: "Movimentação registrada com sucesso!",
      invalidate: [...INV, ["movements", product.id]],
      onSuccess: onClose,
    },
  );
  const adjust = v.type === "ADJUST";
  return (
    <Modal
      open
      onClose={onClose}
      title={`Movimentar: ${product.name}`}
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => save.mutate(undefined)}
            loading={save.isPending}
            disabled={(!adjust && !(v.quantity > 0)) || (adjust && (v.quantity < 0 || !v.reason.trim()))}
          >
            Registrar
          </Button>
        </>
      }
    >
      <FormGrid $columns={2}>
        <FullRow>
          <Muted>
            Saldo atual: {qty(product.currentStock)} {product.unit}
          </Muted>
        </FullRow>
        <SelectField
          label="Tipo"
          value={v.type}
          onChange={(e) => setV({ ...v, type: e.target.value })}
          options={[
            { value: "IN", label: "Entrada (compra, doação)" },
            { value: "OUT", label: "Saída (uso, perda)" },
            { value: "ADJUST", label: "Ajuste de inventário (contagem)" },
          ]}
        />
        <TextField
          label={adjust ? "Saldo contado" : "Quantidade"}
          type="number"
          min={0}
          step="0.001"
          value={v.quantity}
          onChange={(e) => setV({ ...v, quantity: Number(e.target.value) })}
        />
        {v.type === "IN" && (
          <>
            <TextField
              label="Custo unitário (R$)"
              type="number"
              min={0}
              step="0.01"
              value={v.unitCost}
              onChange={(e) => setV({ ...v, unitCost: e.target.value })}
            />
            <TextField
              label="Lote"
              maxLength={60}
              value={v.lotNumber}
              onChange={(e) => setV({ ...v, lotNumber: e.target.value })}
            />
            <TextField
              label="Validade"
              type="date"
              value={v.expiryDate}
              onChange={(e) => setV({ ...v, expiryDate: e.target.value })}
            />
          </>
        )}
        <FullRow>
          <TextField
            label={adjust ? "Motivo do ajuste" : "Motivo / observação"}
            required={adjust}
            maxLength={300}
            value={v.reason}
            onChange={(e) => setV({ ...v, reason: e.target.value })}
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

const TYPE_LABEL: Record<string, { label: string; tone: "success" | "danger" | "info" }> = {
  IN: { label: "Entrada", tone: "success" },
  OUT: { label: "Saída", tone: "danger" },
  ADJUST: { label: "Ajuste", tone: "info" },
};

function MovementsHistory({ product, onClose }: { product: Product; onClose: () => void }) {
  const { data, isPending } = useQuery({
    queryKey: ["movements", product.id],
    queryFn: () => api.inventory.movements(product.id),
  });
  const search = useRowSearch(data, (m) => [m.createdByName]);
  return (
    <Modal open onClose={onClose} title={`Movimentações: ${product.name}`} size="lg">
      <Stack $gap={4}>
        <SearchInput
          label="Buscar por nome"
          placeholder="Quem registrou…"
          value={search.query}
          onChange={search.setQuery}
        />
        <DataTable<StockMovement>
          caption="Movimentações"
          rows={search.rows}
          loading={isPending}
          rowKey={(m) => m.id}
          emptyTitle="Nenhuma movimentação"
          columns={[
            { key: "date", header: "Data", render: (m) => formatDateTime(m.createdAt) },
            {
              key: "type",
              header: "Tipo",
              render: (m) => <Badge $tone={TYPE_LABEL[m.type]?.tone}>{TYPE_LABEL[m.type]?.label}</Badge>,
            },
            {
              key: "qty",
              header: "Qtd.",
              align: "right",
              render: (m) => (Number(m.quantity) > 0 ? "+" : "") + qty(m.quantity),
            },
            { key: "balance", header: "Saldo", align: "right", render: (m) => qty(m.balanceAfter) },
            {
              key: "lot",
              header: "Lote / validade",
              hideOnMobile: true,
              render: (m) => [m.lotNumber, m.expiryDate && formatDate(m.expiryDate)].filter(Boolean).join(" · ") || "—",
            },
            {
              key: "cost",
              header: "Custo",
              hideOnMobile: true,
              render: (m) => (m.unitCost ? formatMoney(m.unitCost) : "—"),
            },
            { key: "reason", header: "Motivo", hideOnMobile: true, render: (m) => m.reason ?? "—" },
            { key: "who", header: "Por", hideOnMobile: true, render: (m) => m.createdByName ?? "—" },
          ]}
        />
      </Stack>
    </Modal>
  );
}

function AlertsTab() {
  const { data, isPending } = useQuery({ queryKey: ["inventory-alerts"], queryFn: api.inventory.alerts });
  const search = useRowSearch(data?.lowStock, (p) => [p.name, p.supplierName]);
  return (
    <Stack $gap={5}>
      <section>
        <h3 style={{ marginBottom: 12 }}>Estoque abaixo do mínimo</h3>
        <div style={{ marginBottom: 12 }}>
          <SearchInput
            label="Buscar por nome"
            placeholder="Produto ou fornecedor…"
            value={search.query}
            onChange={search.setQuery}
          />
        </div>
        <DataTable<Product>
          caption="Produtos com estoque baixo"
          rows={search.rows}
          loading={isPending}
          rowKey={(p) => p.id}
          emptyTitle="Nenhum produto abaixo do mínimo"
          columns={[
            { key: "name", header: "Produto", render: (p) => <strong>{p.name}</strong> },
            { key: "stock", header: "Saldo", align: "right", render: (p) => `${qty(p.currentStock)} ${p.unit}` },
            { key: "min", header: "Mínimo", align: "right", render: (p) => `${qty(p.minStock)} ${p.unit}` },
            { key: "supplier", header: "Fornecedor", render: (p) => p.supplierName ?? "—" },
          ]}
        />
      </section>
      <section>
        <h3 style={{ marginBottom: 12 }}>Lotes vencendo em até 60 dias</h3>
        <DataTable
          caption="Lotes próximos do vencimento"
          rows={data?.expiring}
          loading={isPending}
          rowKey={(l) => `${l.productId}-${l.lotNumber}-${l.expiryDate}`}
          emptyTitle="Nenhum lote próximo do vencimento"
          columns={[
            { key: "product", header: "Produto", render: (l) => <strong>{l.productName}</strong> },
            { key: "lot", header: "Lote", render: (l) => l.lotNumber ?? "—" },
            {
              key: "expiry",
              header: "Validade",
              render: (l) =>
                new Date(l.expiryDate) < new Date() ? (
                  <Badge $tone="danger">Vencido em {formatDate(l.expiryDate)}</Badge>
                ) : (
                  <Badge $tone="warning">{formatDate(l.expiryDate)}</Badge>
                ),
            },
          ]}
        />
      </section>
    </Stack>
  );
}

const Bar = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${({ theme }) => theme.space[4]};
`;

const StockValue = styled.span<{ $low: boolean }>`
  font-weight: ${({ theme }) => theme.fontWeights.bold};
  color: ${({ theme, $low }) => ($low ? theme.colors.danger : theme.colors.text)};
  small {
    font-weight: normal;
    color: ${({ theme }) => theme.colors.textMuted};
  }
`;
