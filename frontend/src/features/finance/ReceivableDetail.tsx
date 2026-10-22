"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import styled from "styled-components";
import { PdfButton } from "@/components/domain/PdfButton";
import { ReasonDialog } from "@/components/domain/ReasonDialog";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Badge, Card, DetailList, Muted, Row, Stack } from "@/components/ui/display";
import { FormGrid, SelectField } from "@/components/ui/fields";
import { LoadingState } from "@/components/ui/LoadingState";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { Installment, Payment, PaymentMethod } from "@/lib/api/types";
import { formatDate, formatDateTime, formatMoney, roundMoney } from "@/lib/format";
import { FINANCE_STATUS, methodLabel, PAYMENT_METHODS } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";

const INV = [["receivables"], ["installments"], ["finance-summary"], ["cash-flow"], ["dashboard"]];

/** Detalhe da conta a receber: parcelas, recebimentos, estornos e cancelamento. */
export function ReceivableDetail({ receivableId, onClose }: { receivableId: string | null; onClose: () => void }) {
  const can = useCan();
  const writable = can(P.FINANCE_WRITE);
  const [paying, setPaying] = useState<Installment | null>(null);
  const [refunding, setRefunding] = useState<Payment | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const r = useQuery({
    queryKey: ["receivables", "detail", receivableId],
    queryFn: () => api.finance.receivable(receivableId ?? ""),
    enabled: Boolean(receivableId),
  });
  const cancel = useAction((reason: string) => api.finance.cancelReceivable(receivableId ?? "", reason), {
    success: "Conta cancelada com sucesso!",
    invalidate: INV,
    onSuccess: () => setCancelling(false),
  });

  const data = r.data;
  return (
    <>
      <Modal open={Boolean(receivableId)} onClose={onClose} title="Conta a receber" size="lg">
        {!data ? (
          <LoadingState />
        ) : (
          <Stack $gap={4}>
            <Row $justify="space-between">
              <div>
                <PatientLink href={`/pacientes/${data.patientId}`}>{data.patientName}</PatientLink>
                <Muted>{data.description}</Muted>
              </div>
              <Badge $tone={FINANCE_STATUS[data.status]?.tone}>{FINANCE_STATUS[data.status]?.label}</Badge>
            </Row>
            <DetailList>
              <div>
                <dt>Total</dt>
                <dd>{formatMoney(data.total)}</dd>
              </div>
              <div>
                <dt>Recebido</dt>
                <dd>{formatMoney(data.paid)}</dd>
              </div>
              <div>
                <dt>Saldo</dt>
                <dd>
                  <strong>{formatMoney(data.balance)}</strong>
                </dd>
              </div>
              {data.cancelReason && (
                <div>
                  <dt>Motivo do cancelamento</dt>
                  <dd>{data.cancelReason}</dd>
                </div>
              )}
            </DetailList>

            {data.installments.map((i) => (
              <Card key={i.id}>
                <Row $justify="space-between">
                  <div>
                    <strong>
                      Parcela {i.number}/{data.installments.length}
                    </strong>{" "}
                    · vence {formatDate(i.dueDate)}{" "}
                    {i.overdue ? (
                      <Badge $tone="danger">Em atraso</Badge>
                    ) : (
                      <Badge $tone={FINANCE_STATUS[i.status]?.tone}>{FINANCE_STATUS[i.status]?.label}</Badge>
                    )}
                    <Muted>
                      {" "}
                      {formatMoney(i.paidAmount)} de {formatMoney(i.amount)}
                    </Muted>
                  </div>
                  {writable && i.status === "OPEN" && <Button onClick={() => setPaying(i)}>Receber</Button>}
                </Row>
                {i.payments.length > 0 && (
                  <Payments>
                    {i.payments.map((p) => (
                      <li key={p.id}>
                        <span>
                          {formatDateTime(p.paidAt)} · {methodLabel(p.method)} ·{" "}
                          <strong>{formatMoney(p.amount)}</strong>
                          {Number(p.refundedAmount) > 0 && (
                            <Refunded> (estornado {formatMoney(p.refundedAmount)})</Refunded>
                          )}
                          {p.createdByName && <Muted> · {p.createdByName}</Muted>}
                        </span>
                        <Row $gap={1} $wrap={false}>
                          <PdfButton
                            variant="ghost"
                            label="Recibo"
                            load={() => ({
                              kind: "receipt",
                              patientId: data.patientId,
                              data: {
                                ...p,
                                description: data.description,
                                installment:
                                  data.installments.length > 1 ? `${i.number}/${data.installments.length}` : null,
                              },
                            })}
                          />
                          {writable && Number(p.refundedAmount) < Number(p.amount) && (
                            <Button variant="ghost" onClick={() => setRefunding(p)}>
                              Estornar
                            </Button>
                          )}
                        </Row>
                      </li>
                    ))}
                  </Payments>
                )}
              </Card>
            ))}

            {writable && data.status === "OPEN" && Number(data.paid) === 0 && (
              <Row $justify="flex-end">
                <Button variant="ghost" onClick={() => setCancelling(true)}>
                  Cancelar conta
                </Button>
              </Row>
            )}
          </Stack>
        )}
      </Modal>
      {paying && <PayDialog installment={paying} onClose={() => setPaying(null)} />}
      {refunding && <RefundDialog payment={refunding} onClose={() => setRefunding(null)} />}
      <ReasonDialog
        open={cancelling}
        title="Cancelar conta a receber"
        confirmLabel="Cancelar conta"
        loading={cancel.isPending}
        onConfirm={(reason) => cancel.mutate(reason)}
        onClose={() => setCancelling(false)}
      />
    </>
  );
}

export function PayDialog({
  installment,
  onClose,
}: {
  installment: { id: string; balance: number; number: number };
  onClose: () => void;
}) {
  const [amount, setAmount] = useState(Number(installment.balance));
  const [method, setMethod] = useState<PaymentMethod>("PIX");
  const [notes, setNotes] = useState("");
  const pay = useAction(() => api.finance.pay(installment.id, { amount, method, notes: notes || undefined }), {
    success: "Recebimento registrado com sucesso!",
    invalidate: INV,
    onSuccess: onClose,
  });
  return (
    <Modal
      open
      onClose={onClose}
      title={`Receber parcela ${installment.number}`}
      size="sm"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => pay.mutate(undefined)}
            loading={pay.isPending}
            disabled={!(amount > 0) || amount > Number(installment.balance)}
          >
            Confirmar recebimento
          </Button>
        </>
      }
    >
      <FormGrid $columns={1}>
        <TextField
          label="Valor (R$)"
          type="number"
          step="0.01"
          min={0.01}
          max={installment.balance}
          value={amount}
          onChange={(e) => setAmount(Number(e.target.value))}
          hint={`Saldo da parcela: ${formatMoney(installment.balance)}. Pode receber parcialmente.`}
        />
        <SelectField
          label="Forma de pagamento"
          value={method}
          onChange={(e) => setMethod(e.target.value as PaymentMethod)}
          options={PAYMENT_METHODS}
        />
        <TextField label="Observação" maxLength={300} value={notes} onChange={(e) => setNotes(e.target.value)} />
        {pay.error && <Alert tone="danger">{pay.error.message}</Alert>}
      </FormGrid>
    </Modal>
  );
}

function RefundDialog({ payment, onClose }: { payment: Payment; onClose: () => void }) {
  const max = roundMoney(Number(payment.amount) - Number(payment.refundedAmount));
  const [amount, setAmount] = useState(max);
  const [reason, setReason] = useState("");
  const refund = useAction(() => api.finance.refund(payment.id, roundMoney(amount), reason), {
    success: "Estorno registrado com sucesso!",
    invalidate: INV,
    onSuccess: onClose,
  });
  return (
    <Modal
      open
      onClose={onClose}
      title="Estornar recebimento"
      size="sm"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Voltar
          </Button>
          <Button
            variant="danger"
            onClick={() => refund.mutate(undefined)}
            loading={refund.isPending}
            disabled={!(amount > 0) || amount > max || !reason.trim()}
          >
            Estornar
          </Button>
        </>
      }
    >
      <FormGrid $columns={1}>
        <Muted>O recebimento original continua registrado; o estorno fica no histórico com o motivo.</Muted>
        <TextField
          label="Valor do estorno (R$)"
          type="number"
          step="0.01"
          min={0.01}
          max={max}
          value={amount}
          onChange={(e) => setAmount(Number(e.target.value))}
        />
        <TextField label="Motivo" required maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} />
        {refund.error && <Alert tone="danger">{refund.error.message}</Alert>}
      </FormGrid>
    </Modal>
  );
}

const PatientLink = styled(Link)`
  display: block;
  font-size: ${({ theme }) => theme.fontSizes.lg};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  color: ${({ theme }) => theme.colors.text};
`;

const Payments = styled.ul`
  list-style: none;
  margin: ${({ theme }) => theme.space[3]} 0 0;
  padding: ${({ theme }) => theme.space[3]} 0 0;
  border-top: 1px solid ${({ theme }) => theme.colors.border};
  display: grid;
  gap: ${({ theme }) => theme.space[2]};
  font-size: ${({ theme }) => theme.fontSizes.sm};

  li {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: ${({ theme }) => theme.space[2]};
  }
`;

const Refunded = styled.span`
  color: ${({ theme }) => theme.colors.danger};
`;
