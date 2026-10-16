"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowDown, ArrowUp, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Card, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow, SelectField } from "@/components/ui/fields";
import { IconButton } from "@/components/ui/IconButton";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { AnamnesisForm, Question } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { FORM_KINDS } from "@/lib/labels";
import { useAction } from "@/lib/mutations";

const KIND_OPTIONS = Object.entries(FORM_KINDS).map(([value, k]) => ({ value, label: k.label }));

/**
 * Fichas de anamnese da clínica: odontológica, harmonização orofacial e outras que a clínica criar. Alterar uma
 * ficha não muda anamneses já preenchidas (cada uma guarda as perguntas da época).
 */
export function FormsManager() {
  const forms = useQuery({ queryKey: ["anamnesis-forms", true], queryFn: () => api.clinic.anamnesisForms(true) });
  const [editing, setEditing] = useState<AnamnesisForm | "new" | null>(null);
  const toggle = useAction((f: AnamnesisForm) => api.clinic.setAnamnesisFormActive(f.id, !f.active), {
    success: "Ficha atualizada com sucesso!",
    invalidate: [["anamnesis-forms"]],
  });

  return (
    <Stack $gap={4}>
      <Alert tone="info">
        As perguntas iniciais são um ponto de partida e devem ser revisadas pelo responsável técnico da clínica.
        Perguntas marcadas com alerta destacam a resposta “sim” no prontuário.
      </Alert>
      <Row $justify="flex-end">
        <Button onClick={() => setEditing("new")}>
          <Plus size={16} aria-hidden /> Nova ficha
        </Button>
      </Row>
      <DataTable<AnamnesisForm>
        caption="Fichas de anamnese"
        rows={forms.data}
        loading={forms.isPending}
        rowKey={(f) => f.id}
        onRowClick={setEditing}
        emptyTitle="Nenhuma ficha"
        columns={[
          { key: "name", header: "Ficha", render: (f) => <strong>{f.name}</strong> },
          {
            key: "kind",
            header: "Tipo",
            render: (f) => <Badge $tone={FORM_KINDS[f.kind]?.tone}>{FORM_KINDS[f.kind]?.label}</Badge>,
          },
          { key: "questions", header: "Perguntas", align: "right", render: (f) => f.questions.length },
          { key: "updated", header: "Atualizada em", hideOnMobile: true, render: (f) => formatDateTime(f.updatedAt) },
          {
            key: "status",
            header: "Situação",
            render: (f) => (f.active ? <Badge $tone="success">Em uso</Badge> : <Badge>Desativada</Badge>),
          },
          {
            key: "actions",
            header: "",
            render: (f) => (
              <Button
                variant="ghost"
                onClick={(e) => {
                  e.stopPropagation();
                  toggle.mutate(f);
                }}
              >
                {f.active ? "Desativar" : "Reativar"}
              </Button>
            ),
          },
        ]}
      />
      {editing && (
        <FormEditor
          form={editing === "new" ? null : editing}
          others={forms.data ?? []}
          onClose={() => setEditing(null)}
        />
      )}
    </Stack>
  );
}

function FormEditor({
  form,
  others,
  onClose,
}: {
  form: AnamnesisForm | null;
  others: AnamnesisForm[];
  onClose: () => void;
}) {
  const [name, setName] = useState(form?.name ?? "");
  const [kind, setKind] = useState<string>(form?.kind ?? "OTHER");
  const [questions, setQuestions] = useState<Question[]>(form?.questions ?? []);
  const save = useAction(
    () =>
      form
        ? api.clinic.updateAnamnesisForm(form.id, { name, kind, questions })
        : api.clinic.createAnamnesisForm({ name, kind, questions }),
    { success: "Ficha salva com sucesso!", invalidate: [["anamnesis-forms"]], onSuccess: onClose },
  );

  const update = (i: number, patch: Partial<Question>) =>
    setQuestions(questions.map((q, j) => (j === i ? { ...q, ...patch } : q)));
  const move = (i: number, dir: -1 | 1) => {
    const next = [...questions];
    const [item] = next.splice(i, 1);
    if (item) next.splice(i + dir, 0, item);
    setQuestions(next);
  };
  const invalid = !name.trim() || questions.length === 0 || questions.some((q) => !q.text.trim() || !q.section.trim());

  return (
    <Modal
      open
      onClose={onClose}
      title={form ? `Editar ficha: ${form.name}` : "Nova ficha de anamnese"}
      size="lg"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => save.mutate(undefined)} loading={save.isPending} disabled={invalid}>
            Salvar ficha
          </Button>
        </>
      }
    >
      <Stack $gap={4}>
        <FormGrid $columns={2}>
          <TextField
            label="Nome da ficha"
            required
            maxLength={120}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <SelectField label="Tipo" value={kind} onChange={(e) => setKind(e.target.value)} options={KIND_OPTIONS} />
          {!form && others.length > 0 && (
            <FullRow>
              <SelectField
                label="Começar a partir de"
                placeholder="Ficha em branco"
                value=""
                onChange={(e) => {
                  const base = others.find((o) => o.id === e.target.value);
                  if (base) setQuestions(base.questions.map((q) => ({ ...q })));
                }}
                options={others.map((o) => ({ value: o.id, label: `Copiar perguntas de: ${o.name}` }))}
              />
            </FullRow>
          )}
        </FormGrid>
        {questions.map((q, i) => (
          <QuestionCard key={`${q.id}-${i}`}>
            <FormGrid $columns={3}>
              <FullRow>
                <TextField
                  label={`Pergunta ${i + 1}`}
                  maxLength={300}
                  value={q.text}
                  onChange={(e) => update(i, { text: e.target.value })}
                />
              </FullRow>
              <TextField
                label="Seção"
                maxLength={60}
                value={q.section}
                onChange={(e) => update(i, { section: e.target.value })}
              />
              <SelectField
                label="Tipo de resposta"
                value={q.type}
                onChange={(e) => update(i, { type: e.target.value as Question["type"] })}
                options={[
                  { value: "YES_NO", label: "Sim / Não" },
                  { value: "YES_NO_TEXT", label: "Sim / Não + detalhe" },
                  { value: "TEXT", label: "Texto livre" },
                ]}
              />
              <CheckboxField
                label="“Sim” gera alerta no prontuário"
                checked={q.alertOnYes}
                disabled={q.type === "TEXT"}
                onChange={(e) => update(i, { alertOnYes: e.target.checked })}
              />
            </FormGrid>
            <Row $gap={2} $justify="flex-end">
              <IconButton icon={ArrowUp} label="Mover para cima" disabled={i === 0} onClick={() => move(i, -1)} />
              <IconButton
                icon={ArrowDown}
                label="Mover para baixo"
                disabled={i === questions.length - 1}
                onClick={() => move(i, 1)}
              />
              <IconButton
                icon={Trash2}
                label="Remover pergunta"
                tone="danger"
                onClick={() => setQuestions(questions.filter((_, j) => j !== i))}
              />
            </Row>
          </QuestionCard>
        ))}
        <Row>
          <Button
            variant="outline"
            onClick={() =>
              setQuestions([
                ...questions,
                {
                  id: `pergunta_${Date.now().toString(36)}`,
                  section: questions.at(-1)?.section ?? "Saúde geral",
                  text: "",
                  type: "YES_NO",
                  alertOnYes: false,
                },
              ])
            }
          >
            <Plus size={16} aria-hidden /> Adicionar pergunta
          </Button>
        </Row>
        {save.error && <Alert tone="danger">{save.error.message}</Alert>}
      </Stack>
    </Modal>
  );
}

const QuestionCard = styled(Card)`
  display: grid;
  gap: ${({ theme }) => theme.space[3]};
`;
