"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Printer } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import styled from "styled-components";
import { PdfButton } from "@/components/domain/PdfButton";
import { PrintSheet, SignatureLine } from "@/components/domain/PrintSheet";
import { ReasonDialog } from "@/components/domain/ReasonDialog";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Card, DetailList, ErrorState, PageHeader, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid } from "@/components/ui/fields";
import { LoadingState } from "@/components/ui/LoadingState";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { useMe } from "@/features/auth/hooks";
import { api } from "@/lib/api/endpoints";
import type { PlanItem } from "@/lib/api/types";
import { formatDate, formatDateTime, formatMoney, toDateInput } from "@/lib/format";
import { PLAN_STATUS } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { printSheet } from "@/lib/print";
import { useRequiredFields } from "@/lib/useRequiredFields";
import { PlanEditor } from "./PlanEditor";

const ITEM_STATUS: Record<string, { label: string; tone: "neutral" | "success" | "warning" }> = {
  PLANNED: { label: "Pendente", tone: "warning" },
  DONE: { label: "Realizado", tone: "success" },
  CANCELLED: { label: "Cancelado", tone: "neutral" },
};

export function PlanDetail({ planId }: { planId: string }) {
  const can = useCan();
  const { data: me } = useMe();
  const [editing, setEditing] = useState(false);
  const [approving, setApproving] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [printing, setPrinting] = useState(false);
  const plan = useQuery({ queryKey: ["plans", "detail", planId], queryFn: () => api.plans.get(planId) });

  const inv = [["plans"], ["receivables"], ["dashboard"]];
  const propose = useAction(() => api.plans.propose(planId), {
    success: "Orçamento marcado como apresentado com sucesso!",
    invalidate: inv,
  });
  const reject = useAction((r: string) => api.plans.reject(planId, r), {
    success: "Orçamento recusado com sucesso!",
    invalidate: inv,
    onSuccess: () => setRejecting(false),
  });
  const cancel = useAction((r: string) => api.plans.cancel(planId, r), {
    success: "Orçamento cancelado com sucesso!",
    invalidate: inv,
    onSuccess: () => setCancelling(false),
  });

  if (plan.isPending) return <LoadingState />;
  if (plan.error) return <ErrorState error={plan.error} onRetry={plan.refetch} />;
  const p = plan.data;
  const writable = can(P.TREATMENTS_WRITE);
  const editable = writable && ["DRAFT", "PROPOSED"].includes(p.status);

  if (editing) {
    return (
      <Stack>
        <PageHeader title="Editar orçamento" />
        <PlanEditor plan={p} />
      </Stack>
    );
  }

  return (
    <Stack>
      <BackLink href="/tratamentos">
        <ArrowLeft size={16} aria-hidden /> Tratamentos
      </BackLink>
      <PageHeader
        title={p.title}
        description={
          <>
            <Link href={`/pacientes/${p.patientId}`}>{p.patientName}</Link> · {p.professionalName} ·{" "}
            <Badge $tone={PLAN_STATUS[p.status]?.tone}>{PLAN_STATUS[p.status]?.label}</Badge>
          </>
        }
        actions={
          <>
            <PdfButton load={() => ({ kind: "plan", patientId: p.patientId, data: p })} />
            <Button variant="outline" onClick={() => setPrinting(true)}>
              <Printer size={16} aria-hidden /> Imprimir
            </Button>
            {editable && (
              <Button variant="outline" onClick={() => setEditing(true)}>
                Editar
              </Button>
            )}
            {writable && p.status === "DRAFT" && (
              <Button variant="outline" onClick={() => propose.mutate(undefined)} loading={propose.isPending}>
                Marcar como apresentado
              </Button>
            )}
            {editable && <Button onClick={() => setApproving(true)}>Registrar aprovação</Button>}
          </>
        }
      />

      {p.status === "APPROVED" && (
        <Alert tone="success">
          Aprovado em {formatDateTime(p.approvedAt)} por {p.approvedByName}. Os itens são baixados automaticamente
          quando o procedimento é registrado no prontuário.
          {p.receivableId && (
            <>
              {" "}
              <Link href={`/financeiro?conta=${p.receivableId}`}>Ver conta a receber</Link>
            </>
          )}
        </Alert>
      )}

      <DataTable<PlanItem>
        caption="Itens do orçamento"
        rows={p.items}
        rowKey={(i) => i.id}
        columns={[
          { key: "desc", header: "Procedimento", render: (i) => i.description },
          {
            key: "tooth",
            header: "Dente",
            render: (i) => (i.tooth ? `${i.tooth}${i.surfaces ? ` (${i.surfaces})` : ""}` : "—"),
          },
          { key: "qty", header: "Qtd.", align: "right", render: (i) => i.quantity },
          { key: "unit", header: "Valor unit.", align: "right", render: (i) => formatMoney(i.unitPrice) },
          {
            key: "disc",
            header: "Desconto",
            align: "right",
            hideOnMobile: true,
            render: (i) => formatMoney(i.discount),
          },
          { key: "total", header: "Total", align: "right", render: (i) => <strong>{formatMoney(i.total)}</strong> },
          {
            key: "status",
            header: "Execução",
            render: (i) => <Badge $tone={ITEM_STATUS[i.status]?.tone}>{ITEM_STATUS[i.status]?.label}</Badge>,
          },
        ]}
      />

      <Card>
        <DetailList>
          <div>
            <dt>Subtotal</dt>
            <dd>{formatMoney(p.subtotal)}</dd>
          </div>
          <div>
            <dt>Descontos</dt>
            <dd>{formatMoney(Number(p.itemsDiscount) + Number(p.discount))}</dd>
          </div>
          <div>
            <dt>Total</dt>
            <dd>
              <strong style={{ fontSize: "1.25rem" }}>{formatMoney(p.total)}</strong>
            </dd>
          </div>
          <div>
            <dt>Condições de pagamento</dt>
            <dd>{p.paymentConditions ?? "—"}</dd>
          </div>
          <div>
            <dt>Validade</dt>
            <dd>{p.validUntil ? formatDate(p.validUntil) : "—"}</dd>
          </div>
          <div>
            <dt>Criado em</dt>
            <dd>{formatDateTime(p.createdAt)}</dd>
          </div>
        </DetailList>
        {p.notes && <Notes>{p.notes}</Notes>}
      </Card>

      {writable && ["DRAFT", "PROPOSED", "APPROVED"].includes(p.status) && (
        <Row $justify="flex-end">
          {["DRAFT", "PROPOSED"].includes(p.status) && (
            <Button variant="ghost" onClick={() => setRejecting(true)}>
              Paciente recusou
            </Button>
          )}
          <Button variant="ghost" onClick={() => setCancelling(true)}>
            Cancelar orçamento
          </Button>
        </Row>
      )}

      {approving && (
        <ApproveDialog
          planId={planId}
          total={Number(p.total)}
          patientName={p.patientName}
          onClose={() => setApproving(false)}
        />
      )}
      <ReasonDialog
        open={rejecting}
        title="Registrar recusa"
        confirmLabel="Registrar recusa"
        loading={reject.isPending}
        onConfirm={(r) => reject.mutate(r)}
        onClose={() => setRejecting(false)}
      />
      <ReasonDialog
        open={cancelling}
        title="Cancelar orçamento"
        description={
          p.receivableId ? "Antes, cancele a conta a receber gerada por este orçamento no Financeiro." : undefined
        }
        confirmLabel="Cancelar orçamento"
        loading={cancel.isPending}
        onConfirm={(r) => cancel.mutate(r)}
        onClose={() => setCancelling(false)}
      />
      <Modal
        open={printing}
        onClose={() => setPrinting(false)}
        title="Orçamento"
        size="lg"
        footer={
          <Button onClick={printSheet}>
            <Printer size={16} aria-hidden /> Imprimir
          </Button>
        }
      >
        <PrintSheet
          clinicName={me?.activeClinic?.name ?? ""}
          title={`Orçamento — ${p.title}`}
          footer={
            <>
              <p>
                Total: <strong>{formatMoney(p.total)}</strong>
                {p.paymentConditions ? ` · ${p.paymentConditions}` : ""}
                {p.validUntil ? ` · Válido até ${formatDate(p.validUntil)}` : ""}
              </p>
              <SignatureLine>{p.patientName}</SignatureLine>
            </>
          }
        >
          {`Paciente: ${p.patientName}\nProfissional: ${p.professionalName}\n\n` +
            p.items
              .filter((i) => i.status !== "CANCELLED")
              .map(
                (i, n) =>
                  `${n + 1}. ${i.description}${i.tooth ? ` (dente ${i.tooth})` : ""} — ${i.quantity} × ${formatMoney(i.unitPrice)}${
                    Number(i.discount) ? ` − ${formatMoney(i.discount)}` : ""
                  } = ${formatMoney(i.total)}`,
              )
              .join("\n") +
            (Number(p.discount) ? `\n\nDesconto geral: ${formatMoney(p.discount)}` : "") +
            (p.notes ? `\n\nObservações: ${p.notes}` : "")}
        </PrintSheet>
      </Modal>
    </Stack>
  );
}

function ApproveDialog({
  planId,
  total,
  patientName,
  onClose,
}: {
  planId: string;
  total: number;
  patientName: string;
  onClose: () => void;
}) {
  const [name, setName] = useState(patientName);
  const form = useRequiredFields({ name: !name.trim() && "Informe quem aprovou o orçamento." });
  const [generate, setGenerate] = useState(true);
  const [installments, setInstallments] = useState(1);
  const [firstDue, setFirstDue] = useState(toDateInput(new Date()));
  const approve = useAction(
    () =>
      api.plans.approve(planId, {
        approvedByName: name,
        generateReceivable: generate,
        installments,
        firstDueDate: firstDue,
      }),
    {
      success: "Aprovação registrada com sucesso!",
      invalidate: [["plans"], ["receivables"], ["dashboard"]],
      onSuccess: onClose,
    },
  );
  return (
    <Modal
      open
      onClose={onClose}
      title="Registrar aprovação do orçamento"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Voltar
          </Button>
          <Button onClick={() => form.check() && approve.mutate(undefined)} loading={approve.isPending}>
            Confirmar aprovação
          </Button>
        </>
      }
    >
      <FormGrid $columns={2}>
        <TextField
          label="Aprovado por"
          required
          error={form.errors.name}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <div />
        <CheckboxField
          label="Gerar conta a receber no Financeiro"
          checked={generate}
          onChange={(e) => setGenerate(e.target.checked)}
        />
        <div />
        {generate && (
          <>
            <TextField
              label="Parcelas"
              type="number"
              min={1}
              max={48}
              value={installments}
              onChange={(e) => setInstallments(Math.min(48, Math.max(1, Number(e.target.value))))}
              hint={`${installments}× de aproximadamente ${formatMoney(total / installments)}`}
            />
            <TextField
              label="1º vencimento"
              type="date"
              value={firstDue}
              onChange={(e) => setFirstDue(e.target.value)}
            />
          </>
        )}
      </FormGrid>
      {approve.error && <Alert tone="danger">{approve.error.message}</Alert>}
    </Modal>
  );
}

const BackLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: ${({ theme }) => theme.fontSizes.sm};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  text-decoration: none;
`;

const Notes = styled.p`
  margin-top: ${({ theme }) => theme.space[4]};
  white-space: pre-wrap;
  color: ${({ theme }) => theme.colors.textMuted};
`;
