"use client";

import { useQuery } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import styled from "styled-components";
import { PatientSelect } from "@/components/domain/PatientSelect";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Card, CardTitle, Muted, Row, Stack } from "@/components/ui/display";
import { DISCARD_CHANGES, useFeedback } from "@/components/ui/Feedback";
import { FormGrid, FullRow, SelectField, TextAreaField } from "@/components/ui/fields";
import { IconButton } from "@/components/ui/IconButton";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { Plan, PlanInput, PlanItemInput } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";
import { useAction } from "@/lib/mutations";

interface ItemDraft extends PlanItemInput {
  key: string;
}

let seq = 0;
const newItem = (): ItemDraft => ({ key: `i${++seq}`, description: "", quantity: 1, unitPrice: 0, discount: 0 });

interface Props {
  plan?: Plan;
  initialPatient?: { id: string; name: string } | null;
}

/** Criação/edição de orçamento. A prévia dos totais é só visual: o servidor recalcula e é a referência. */
export function PlanEditor({ plan, initialPatient = null }: Props) {
  const router = useRouter();
  const feedback = useFeedback();
  // Algo foi preenchido/alterado? Usado para confirmar antes de descartar ao cancelar.
  const dirty = useRef(false);
  const markDirty = () => {
    dirty.current = true;
  };
  const cancel = async () => {
    if (dirty.current && !(await feedback.confirm(DISCARD_CHANGES))) return;
    router.back();
  };
  const professionals = useQuery({ queryKey: ["professionals", true], queryFn: () => api.clinic.professionals(true) });
  const procedures = useQuery({
    queryKey: ["procedures", "active"],
    queryFn: () => api.clinic.procedures(undefined, true),
  });

  const [patient, setPatient] = useState(plan ? { id: plan.patientId, name: plan.patientName } : initialPatient);
  const [professionalId, setProfessionalId] = useState(plan?.professionalId ?? "");
  const [title, setTitle] = useState(plan?.title ?? "Plano de tratamento");
  const [discount, setDiscount] = useState(Number(plan?.discount ?? 0));
  const [paymentConditions, setPaymentConditions] = useState(plan?.paymentConditions ?? "");
  const [notes, setNotes] = useState(plan?.notes ?? "");
  const [validUntil, setValidUntil] = useState(plan?.validUntil ?? "");
  const [items, setItems] = useState<ItemDraft[]>(
    plan?.items
      .filter((i) => i.status !== "CANCELLED")
      .map((i) => ({
        key: i.id,
        procedureId: i.procedureId,
        description: i.description,
        tooth: i.tooth,
        surfaces: i.surfaces,
        quantity: i.quantity,
        unitPrice: Number(i.unitPrice),
        discount: Number(i.discount),
      })) ?? [newItem()],
  );
  const [errors, setErrors] = useState<string[]>([]);

  const subtotal = items.reduce((s, i) => s + i.quantity * i.unitPrice, 0);
  const itemsDiscount = items.reduce((s, i) => s + (i.discount ?? 0), 0);
  const total = Math.max(0, subtotal - itemsDiscount - discount);

  const update = (key: string, patch: Partial<ItemDraft>) =>
    setItems((all) => all.map((i) => (i.key === key ? { ...i, ...patch } : i)));

  const save = useAction(
    () => {
      const body: PlanInput = {
        patientId: patient?.id ?? "",
        professionalId,
        title,
        discount,
        paymentConditions: paymentConditions || null,
        notes: notes || null,
        validUntil: validUntil || null,
        items: items.map(({ procedureId, description, tooth, surfaces, quantity, unitPrice, discount: d }) => ({
          procedureId,
          description,
          quantity,
          unitPrice,
          discount: d,
          tooth: tooth || null,
          surfaces: surfaces || null,
        })),
        version: plan?.version,
      };
      return plan ? api.plans.update(plan.id, body) : api.plans.create(body);
    },
    {
      success: plan ? "Orçamento atualizado com sucesso!" : "Orçamento criado com sucesso!",
      invalidate: [["plans"]],
      onSuccess: (p) => router.push(`/tratamentos/${p.id}`),
    },
  );

  const submit = () => {
    const e: string[] = [];
    if (!patient) e.push("Selecione o paciente.");
    if (!professionalId) e.push("Selecione o profissional.");
    if (!title.trim()) e.push("Informe o título.");
    if (items.length === 0) e.push("Inclua ao menos um item.");
    items.forEach((i, n) => {
      if (!i.description.trim()) e.push(`Item ${n + 1}: informe a descrição.`);
      if ((i.discount ?? 0) > i.quantity * i.unitPrice) e.push(`Item ${n + 1}: desconto maior que o valor.`);
    });
    if (discount > subtotal - itemsDiscount) e.push("O desconto geral é maior que o valor do orçamento.");
    setErrors(e);
    if (e.length === 0) save.mutate(undefined);
  };

  return (
    <Stack
      onInputCapture={markDirty}
      onChangeCapture={markDirty}
      onClickCapture={(e) => {
        if ((e.target as HTMLElement).closest('[role="option"]')) markDirty();
      }}
    >
      <Card>
        <FormGrid $columns={2}>
          <FullRow>
            <PatientSelect value={patient} onChange={setPatient} required disabled={Boolean(plan)} />
          </FullRow>
          <SelectField
            label="Profissional responsável"
            required
            placeholder="Selecione…"
            value={professionalId}
            onChange={(e) => setProfessionalId(e.target.value)}
            options={(professionals.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
          />
          <TextField label="Título" required maxLength={160} value={title} onChange={(e) => setTitle(e.target.value)} />
        </FormGrid>
      </Card>

      <Card>
        <CardTitle>
          Procedimentos
          <Button variant="outline" onClick={() => setItems((all) => [...all, newItem()])}>
            <Plus size={16} aria-hidden /> Adicionar item
          </Button>
        </CardTitle>
        <ItemsList>
          {items.map((i, n) => (
            <ItemRow key={i.key} aria-label={`Item ${n + 1}`}>
              <SelectField
                label="Do catálogo"
                placeholder="(livre)"
                value={i.procedureId ?? ""}
                onChange={(e) => {
                  const p = procedures.data?.find((x) => x.id === e.target.value);
                  update(i.key, {
                    procedureId: e.target.value || null,
                    description: p?.name ?? i.description,
                    unitPrice: p ? Number(p.defaultPrice) : i.unitPrice,
                  });
                }}
                options={(procedures.data ?? []).map((p) => ({
                  value: p.id,
                  label: `${p.name} (${formatMoney(p.defaultPrice)})`,
                }))}
              />
              <TextField
                label="Descrição"
                required
                maxLength={200}
                value={i.description}
                onChange={(e) => update(i.key, { description: e.target.value })}
              />
              <TextField
                label="Dente"
                type="number"
                min={11}
                max={85}
                value={i.tooth ?? ""}
                onChange={(e) => update(i.key, { tooth: e.target.value ? Number(e.target.value) : null })}
              />
              <TextField
                label="Qtd."
                type="number"
                min={1}
                max={100}
                value={i.quantity}
                onChange={(e) => update(i.key, { quantity: Math.max(1, Number(e.target.value)) })}
              />
              <TextField
                label="Valor unit. (R$)"
                type="number"
                min={0}
                step="0.01"
                value={i.unitPrice}
                onChange={(e) => update(i.key, { unitPrice: Number(e.target.value) })}
              />
              <TextField
                label="Desconto (R$)"
                type="number"
                min={0}
                step="0.01"
                value={i.discount ?? 0}
                onChange={(e) => update(i.key, { discount: Number(e.target.value) })}
              />
              <ItemTotal>
                <Muted>Total</Muted>
                <strong>{formatMoney(i.quantity * i.unitPrice - (i.discount ?? 0))}</strong>
              </ItemTotal>
              <IconButton
                icon={Trash2}
                label={`Remover item ${n + 1}`}
                tone="danger"
                disabled={items.length === 1}
                onClick={() => setItems((all) => all.filter((x) => x.key !== i.key))}
              />
            </ItemRow>
          ))}
        </ItemsList>
      </Card>

      <Card>
        <FormGrid $columns={3}>
          <TextField
            label="Desconto geral (R$)"
            type="number"
            min={0}
            step="0.01"
            value={discount}
            onChange={(e) => setDiscount(Number(e.target.value))}
          />
          <TextField
            label="Válido até"
            type="date"
            value={validUntil}
            onChange={(e) => setValidUntil(e.target.value)}
          />
          <TextField
            label="Condições de pagamento"
            maxLength={500}
            placeholder="Ex.: até 3x sem juros"
            value={paymentConditions}
            onChange={(e) => setPaymentConditions(e.target.value)}
          />
          <FullRow>
            <TextAreaField
              label="Observações"
              rows={3}
              maxLength={4000}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </FullRow>
        </FormGrid>
        <Totals>
          <span>Subtotal: {formatMoney(subtotal)}</span>
          <span>Descontos: {formatMoney(itemsDiscount + discount)}</span>
          <strong>Total: {formatMoney(total)}</strong>
        </Totals>
      </Card>

      {errors.length > 0 && (
        <Alert tone="danger" title="Corrija antes de salvar">
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            {errors.map((e) => (
              <li key={e}>{e}</li>
            ))}
          </ul>
        </Alert>
      )}
      {save.error && <Alert tone="danger">{save.error.message}</Alert>}

      <Row $justify="flex-end">
        <Button variant="outline" onClick={() => void cancel()}>
          Cancelar
        </Button>
        <Button onClick={submit} loading={save.isPending}>
          {plan ? "Salvar orçamento" : "Criar orçamento"}
        </Button>
      </Row>
    </Stack>
  );
}

const ItemsList = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[4]};
`;

const ItemRow = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[3]};
  align-items: end;
  padding-bottom: ${({ theme }) => theme.space[4]};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  grid-template-columns: 1fr;

  @media (min-width: ${({ theme }) => theme.breakpoints.lg}px) {
    grid-template-columns: 1.4fr 1.6fr 0.6fr 0.5fr 0.9fr 0.9fr 0.8fr auto;
  }
`;

const ItemTotal = styled.div`
  display: grid;
  gap: 2px;
  padding-bottom: 10px;
`;

const Totals = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: ${({ theme }) => theme.space[6]};
  margin-top: ${({ theme }) => theme.space[4]};
  font-size: ${({ theme }) => theme.fontSizes.base};

  strong {
    font-size: ${({ theme }) => theme.fontSizes.lg};
    color: ${({ theme }) => theme.colors.primaryText};
  }
`;
