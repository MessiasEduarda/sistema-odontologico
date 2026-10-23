"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import styled from "styled-components";
import { PdfButton } from "@/components/domain/PdfButton";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Badge, Card, CardTitle, EmptyState, ErrorState, Muted, Row, Stack } from "@/components/ui/display";
import { SelectField, TextAreaField } from "@/components/ui/fields";
import { LoadingState } from "@/components/ui/LoadingState";
import { Modal } from "@/components/ui/Modal";
import { api } from "@/lib/api/endpoints";
import type { Anamnesis, AnamnesisForm as FormDef, Answer, ClinicalAlert, Question } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { FORM_KINDS } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";

export function AnamnesisSection({ patientId }: { patientId: string }) {
  const can = useCan();
  const forms = useQuery({ queryKey: ["anamnesis-forms", false], queryFn: () => api.clinic.anamnesisForms() });
  const history = useQuery({ queryKey: ["anamneses", patientId], queryFn: () => api.clinical.anamneses(patientId) });
  const [selected, setSelected] = useState<string | null>(null);
  const [filling, setFilling] = useState(false);
  const [viewing, setViewing] = useState<Anamnesis | null>(null);

  if (history.isPending || forms.isPending) return <LoadingState />;
  const failed = history.isError ? history : forms.isError ? forms : null;
  if (failed) {
    return (
      <ErrorState
        error={failed.error}
        onRetry={() => {
          void history.refetch();
          void forms.refetch();
        }}
      />
    );
  }

  const all = history.data ?? [];
  const tabs: { key: string; name: string; kind: string; form?: FormDef }[] = (forms.data ?? []).map((f) => ({
    key: f.id,
    name: f.name,
    kind: f.kind,
    form: f,
  }));
  for (const a of all) {
    const key = a.formId ?? a.formName;
    if (!tabs.some((t) => t.key === key)) tabs.push({ key, name: a.formName, kind: a.formKind });
  }
  const current = tabs.find((t) => t.key === selected) ?? tabs[0];
  const versions = all.filter((a) => (a.formId ?? a.formName) === current?.key);
  const latest = versions[0];

  return (
    <Stack $gap={4}>
      {tabs.length > 1 && (
        <FormSwitch role="tablist" aria-label="Fichas de anamnese">
          {tabs.map((t) => {
            const count = all.filter((a) => (a.formId ?? a.formName) === t.key).length;
            return (
              <button
                key={t.key}
                type="button"
                role="tab"
                aria-selected={t.key === current?.key}
                onClick={() => setSelected(t.key)}
              >
                {t.name}
                <Badge $tone={FORM_KINDS[t.kind]?.tone}>{count > 0 ? `${count} versão(ões)` : "pendente"}</Badge>
              </button>
            );
          })}
        </FormSwitch>
      )}
      <Header>
        <Muted>
          {latest
            ? `${current?.name}: atualizada em ${formatDateTime(latest.createdAt)} por ${latest.createdByName ?? "—"}`
            : `${current?.name ?? "Anamnese"}: ainda não preenchida.`}
        </Muted>
        <Row $gap={2}>
          {latest && <PdfButton load={() => ({ kind: "anamnesis", patientId, data: latest })} />}
          {can(P.CLINICAL_WRITE) && current?.form && (
            <Button onClick={() => setFilling(true)}>{latest ? "Atualizar ficha" : "Preencher ficha"}</Button>
          )}
        </Row>
      </Header>

      {latest ? (
        <>
          {latest.alerts.length > 0 && <AlertsBox alerts={latest.alerts} />}
          <AnswersView anamnesis={latest} />
          {versions.length > 1 && (
            <Card>
              <CardTitle>Versões anteriores</CardTitle>
              <Versions>
                {versions.slice(1).map((a) => (
                  <li key={a.id}>
                    <Button variant="ghost" onClick={() => setViewing(a)}>
                      {formatDateTime(a.createdAt)} · {a.createdByName ?? "—"} · {a.alerts.length} alerta(s)
                    </Button>
                  </li>
                ))}
              </Versions>
            </Card>
          )}
        </>
      ) : (
        <EmptyState title="Ficha não preenchida" description="Registre o histórico de saúde antes do procedimento." />
      )}

      {filling && current?.form && (
        <AnamnesisForm patientId={patientId} form={current.form} previous={latest} onClose={() => setFilling(false)} />
      )}
      <Modal
        open={Boolean(viewing)}
        onClose={() => setViewing(null)}
        title="Anamnese (versão anterior)"
        size="lg"
        footer={viewing && <PdfButton load={() => ({ kind: "anamnesis", patientId, data: viewing })} />}
      >
        {viewing && <AnswersView anamnesis={viewing} />}
      </Modal>
    </Stack>
  );
}

export function AlertsBox({ alerts }: { alerts: ClinicalAlert[] }) {
  return (
    <Alert tone="danger" title="Atenção">
      <ul style={{ margin: 0, paddingLeft: 18 }}>
        {alerts.map((a) => (
          <li key={a.questionId}>
            {a.text}
            {a.detail ? ` — ${a.detail}` : ""}
          </li>
        ))}
      </ul>
    </Alert>
  );
}

export function AnswersView({ anamnesis }: { anamnesis: Anamnesis }) {
  const sections = groupBySection(anamnesis.questions);
  return (
    <Stack $gap={4}>
      {sections.map(([section, questions]) => (
        <Card key={section}>
          <CardTitle>{section}</CardTitle>
          <AnswerList>
            {questions.map((q) => {
              const a = anamnesis.answers[q.id];
              const yes = a?.answer === "YES";
              return (
                <div key={q.id}>
                  <dt>{q.text}</dt>
                  <dd>
                    {q.type !== "TEXT" && (
                      <Badge
                        $tone={yes ? (q.alertOnYes ? "danger" : "primary") : a?.answer === "NO" ? "neutral" : "neutral"}
                      >
                        {a?.answer === "YES" ? "Sim" : a?.answer === "NO" ? "Não" : "Sem resposta"}
                      </Badge>
                    )}
                    {a?.detail && <span> {a.detail}</span>}
                    {q.type === "TEXT" && !a?.detail && <Muted>—</Muted>}
                  </dd>
                </div>
              );
            })}
          </AnswerList>
        </Card>
      ))}
      {anamnesis.notes && (
        <Card>
          <CardTitle>Observações</CardTitle>
          <p style={{ whiteSpace: "pre-wrap" }}>{anamnesis.notes}</p>
        </Card>
      )}
    </Stack>
  );
}

export function AnamnesisForm({
  patientId,
  form,
  previous,
  onClose,
}: {
  patientId: string;
  form: FormDef;
  previous?: Anamnesis;
  onClose: () => void;
}) {
  const [answers, setAnswers] = useState<Record<string, Answer>>(() => previous?.answers ?? {});
  const [notes, setNotes] = useState(previous?.notes ?? "");
  const [filledBy, setFilledBy] = useState("PROFESSIONAL");
  const save = useAction(() => api.clinical.fillAnamnesis(patientId, { formId: form.id, answers, notes, filledBy }), {
    success: "Anamnese registrada com sucesso!",
    invalidate: [["anamneses", patientId], ["record", patientId], ["recent-anamneses"]],
    onSuccess: onClose,
  });

  const questions = form.questions;
  const set = (id: string, patch: Answer) => setAnswers((all) => ({ ...all, [id]: { ...all[id], ...patch } }));

  return (
    <Modal
      open
      onClose={onClose}
      title={form.name}
      size="lg"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose} disabled={save.isPending}>
            Cancelar
          </Button>
          <Button onClick={() => save.mutate(undefined)} loading={save.isPending}>
            Salvar anamnese
          </Button>
        </>
      }
    >
      {
        <Stack $gap={5}>
          <Alert tone="info">
            Respostas marcadas em vermelho geram alerta no prontuário. As respostas anteriores vêm preenchidas para
            revisão.
          </Alert>
          {groupBySection(questions).map(([section, qs]) => (
            <fieldset key={section} style={{ border: "none", padding: 0, margin: 0 }}>
              <Legend>{section}</Legend>
              <Stack $gap={3}>
                {qs.map((q) => (
                  <QuestionRow key={q.id}>
                    <QText id={`q-${q.id}`}>
                      {q.text}
                      {q.alertOnYes && <AlertMark title="Resposta 'sim' gera alerta"> ●</AlertMark>}
                    </QText>
                    {q.type !== "TEXT" && (
                      <YesNo role="radiogroup" aria-labelledby={`q-${q.id}`}>
                        {(["YES", "NO"] as const).map((opt) => (
                          <label key={opt}>
                            <input
                              type="radio"
                              name={q.id}
                              checked={answers[q.id]?.answer === opt}
                              onChange={() => set(q.id, { answer: opt })}
                            />
                            <span>{opt === "YES" ? "Sim" : "Não"}</span>
                          </label>
                        ))}
                      </YesNo>
                    )}
                    {(q.type === "TEXT" || (q.type === "YES_NO_TEXT" && answers[q.id]?.answer === "YES")) && (
                      <DetailInput
                        aria-label={`Detalhe: ${q.text}`}
                        placeholder={q.type === "TEXT" ? "Resposta" : "Qual? Detalhe"}
                        maxLength={1000}
                        value={answers[q.id]?.detail ?? ""}
                        onChange={(e) => set(q.id, { detail: e.target.value })}
                      />
                    )}
                  </QuestionRow>
                ))}
              </Stack>
            </fieldset>
          ))}
          <TextAreaField
            label="Observações"
            rows={3}
            maxLength={4000}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
          <SelectField
            label="Preenchido por"
            value={filledBy}
            onChange={(e) => setFilledBy(e.target.value)}
            options={[
              { value: "PROFESSIONAL", label: "Profissional (entrevista)" },
              { value: "PATIENT", label: "Paciente (autopreenchimento)" },
            ]}
          />
          {save.error && <Alert tone="danger">{save.error.message}</Alert>}
        </Stack>
      }
    </Modal>
  );
}

function groupBySection(questions: Question[]): [string, Question[]][] {
  const map = new Map<string, Question[]>();
  for (const q of questions) map.set(q.section, [...(map.get(q.section) ?? []), q]);
  return [...map.entries()];
}

const Header = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: ${({ theme }) => theme.space[3]};
`;

const AnswerList = styled.dl`
  margin: 0;
  display: grid;
  gap: ${({ theme }) => theme.space[3]};

  div {
    display: grid;
    gap: 2px;
  }
  dt {
    font-size: ${({ theme }) => theme.fontSizes.sm};
    color: ${({ theme }) => theme.colors.textMuted};
  }
  dd {
    margin: 0;
  }
`;

const Versions = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
`;

const Legend = styled.legend`
  font-weight: ${({ theme }) => theme.fontWeights.bold};
  color: ${({ theme }) => theme.colors.primaryText};
  margin-bottom: ${({ theme }) => theme.space[3]};
  padding: 0;
`;

const QuestionRow = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[2]};
  padding-bottom: ${({ theme }) => theme.space[3]};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
`;

const QText = styled.span`
  font-weight: ${({ theme }) => theme.fontWeights.medium};
`;

const AlertMark = styled.span`
  color: ${({ theme }) => theme.colors.danger};
`;

const YesNo = styled.div`
  display: inline-flex;
  gap: ${({ theme }) => theme.space[2]};

  label {
    position: relative;
    cursor: pointer;
  }
  input {
    position: absolute;
    inset: 0;
    opacity: 0;
    margin: 0;
    cursor: pointer;
  }
  span {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 64px;
    min-height: 34px;
    padding: 0 ${({ theme }) => theme.space[4]};
    border: 1px solid ${({ theme }) => theme.colors.borderStrong};
    border-radius: ${({ theme }) => theme.radii.pill};
    background: ${({ theme }) => theme.colors.surface};
    color: ${({ theme }) => theme.colors.textMuted};
    font-size: ${({ theme }) => theme.fontSizes.sm};
    font-weight: ${({ theme }) => theme.fontWeights.semibold};
    transition:
      background ${({ theme }) => theme.transitions.fast},
      border-color ${({ theme }) => theme.transitions.fast};
  }
  label:hover span {
    border-color: ${({ theme }) => theme.colors.primary};
  }
  input:checked + span {
    background: ${({ theme }) => theme.colors.primary};
    border-color: ${({ theme }) => theme.colors.primary};
    color: ${({ theme }) => theme.colors.onPrimary};
  }
  input:focus-visible + span {
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
`;

const DetailInput = styled.input`
  min-height: 40px;
  padding: 0 ${({ theme }) => theme.space[4]};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radii.pill};
`;

const FormSwitch = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[2]};

  button {
    display: inline-flex;
    align-items: center;
    gap: ${({ theme }) => theme.space[2]};
    min-height: 40px;
    padding: 0 ${({ theme }) => theme.space[4]};
    border-radius: ${({ theme }) => theme.radii.pill};
    border: 1px solid ${({ theme }) => theme.colors.border};
    background: ${({ theme }) => theme.colors.surface};
    color: ${({ theme }) => theme.colors.text};
    font-weight: ${({ theme }) => theme.fontWeights.medium};
    cursor: pointer;
  }
  button[aria-selected="true"] {
    border-color: ${({ theme }) => theme.colors.primary};
    box-shadow: inset 0 0 0 1px ${({ theme }) => theme.colors.primary};
  }

  @media (max-width: ${({ theme }) => theme.breakpoints.md - 1}px) {
    button {
      flex: 1 1 100%;
      justify-content: center;
    }
  }
`;
