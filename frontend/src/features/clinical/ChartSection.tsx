"use client";

import { useQuery } from "@tanstack/react-query";
import { Ban } from "lucide-react";
import { useState } from "react";
import styled from "styled-components";
import { Odontogram } from "@/components/domain/Odontogram";
import { ReasonDialog } from "@/components/domain/ReasonDialog";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Card, ErrorState, Muted, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow, SelectField, TextAreaField } from "@/components/ui/fields";
import { IconButton } from "@/components/ui/IconButton";
import { LoadingState } from "@/components/ui/LoadingState";
import { Modal } from "@/components/ui/Modal";
import { api } from "@/lib/api/endpoints";
import type { ChartEntry } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { CHART_STATUS, conditionInfo, TOOTH_CONDITIONS } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";

const FACES = [
  { value: "V", label: "Vestibular (V)" },
  { value: "L", label: "Lingual / Palatina (L/P)" },
  { value: "M", label: "Mesial (M)" },
  { value: "D", label: "Distal (D)" },
  { value: "O", label: "Oclusal / Incisal (O/I)" },
];

export function ChartSection({ patientId }: { patientId: string }) {
  const can = useCan();
  const [dentition, setDentition] = useState<"permanent" | "deciduous">("permanent");
  const [tooth, setTooth] = useState<number | null>(null);
  const [voiding, setVoiding] = useState<ChartEntry | null>(null);
  const chart = useQuery({ queryKey: ["chart", patientId], queryFn: () => api.clinical.chart(patientId) });

  const voidEntry = useAction((reason: string) => api.clinical.voidChart(patientId, voiding?.id ?? "", reason), {
    success: "Marcação anulada com sucesso!",
    invalidate: [["chart", patientId]],
    onSuccess: () => setVoiding(null),
  });

  if (chart.isPending) return <LoadingState />;
  // Falha de carga nunca pode parecer "odontograma vazio": o dentista decidiria sem ver o histórico.
  if (chart.isError) return <ErrorState error={chart.error} onRetry={() => chart.refetch()} />;
  const entries = chart.data ?? [];
  const toothEntries = tooth ? entries.filter((e) => e.tooth === tooth) : entries;

  return (
    <Stack $gap={4}>
      <Card>
        <Row $justify="space-between">
          <Muted>Clique em um dente para ver o histórico e registrar condições.</Muted>
          <Row $gap={2} role="group" aria-label="Dentição">
            <Button
              variant={dentition === "permanent" ? "primary" : "outline"}
              onClick={() => setDentition("permanent")}
            >
              Permanente
            </Button>
            <Button
              variant={dentition === "deciduous" ? "primary" : "outline"}
              onClick={() => setDentition("deciduous")}
            >
              Decídua
            </Button>
          </Row>
        </Row>
        <div style={{ marginTop: 16 }}>
          <Odontogram entries={entries} dentition={dentition} selectedTooth={tooth} onSelectTooth={setTooth} />
        </div>
      </Card>

      <Row $justify="space-between">
        <h3>{tooth ? `Dente ${tooth}` : "Todas as marcações"}</h3>
        <Row $gap={2}>
          {tooth && (
            <Button variant="ghost" onClick={() => setTooth(null)}>
              Ver todos
            </Button>
          )}
          {tooth && can(P.CLINICAL_WRITE) && <AddEntry patientId={patientId} tooth={tooth} />}
        </Row>
      </Row>

      <DataTable<ChartEntry>
        caption="Marcações do odontograma"
        rows={[...toothEntries].reverse()}
        rowKey={(e) => e.id}
        emptyTitle="Nenhuma marcação"
        columns={[
          { key: "tooth", header: "Dente", render: (e) => <strong>{e.tooth}</strong> },
          { key: "faces", header: "Faces", render: (e) => e.surfaces ?? "Dente inteiro" },
          {
            key: "condition",
            header: "Condição",
            render: (e) => (
              <ConditionCell $voided={Boolean(e.voidedAt)}>
                <Swatch style={{ background: conditionInfo(e.condition).color }} aria-hidden />
                {conditionInfo(e.condition).label}
              </ConditionCell>
            ),
          },
          {
            key: "status",
            header: "Situação",
            render: (e) =>
              e.voidedAt ? (
                <Badge title={e.voidReason ?? ""}>Anulada: {e.voidReason}</Badge>
              ) : (
                <Badge $tone={e.status === "PLANNED" ? "warning" : e.status === "DONE" ? "success" : "info"}>
                  {CHART_STATUS[e.status]}
                </Badge>
              ),
          },
          { key: "notes", header: "Obs.", hideOnMobile: true, render: (e) => e.notes ?? "—" },
          {
            key: "when",
            header: "Registro",
            hideOnMobile: true,
            render: (e) => `${formatDateTime(e.createdAt)} · ${e.createdByName ?? "—"}`,
          },
          {
            key: "actions",
            header: "",
            render: (e) =>
              !e.voidedAt &&
              can(P.CLINICAL_WRITE) && (
                <IconButton icon={Ban} label="Anular marcação" tone="danger" onClick={() => setVoiding(e)} />
              ),
          },
        ]}
      />
      <ReasonDialog
        open={Boolean(voiding)}
        title="Anular marcação"
        description="A marcação continua no histórico, identificada como anulada, com o motivo."
        confirmLabel="Anular"
        loading={voidEntry.isPending}
        onConfirm={(reason) => voidEntry.mutate(reason)}
        onClose={() => setVoiding(null)}
      />
    </Stack>
  );
}

function AddEntry({ patientId, tooth }: { patientId: string; tooth: number }) {
  const [open, setOpen] = useState(false);
  const [faces, setFaces] = useState<string[]>([]);
  const [condition, setCondition] = useState("CARIES");
  const [status, setStatus] = useState("EXISTING");
  const [notes, setNotes] = useState("");
  const add = useAction(
    () =>
      api.clinical.addChart(patientId, {
        tooth,
        surfaces: faces.length ? faces.join("") : null,
        condition,
        status,
        notes: notes || undefined,
      }),
    {
      success: "Marcação registrada com sucesso!",
      invalidate: [["chart", patientId]],
      onSuccess: () => {
        setOpen(false);
        setFaces([]);
        setNotes("");
      },
    },
  );
  const toggle = (f: string) => setFaces((all) => (all.includes(f) ? all.filter((x) => x !== f) : [...all, f]));

  return (
    <>
      <Button onClick={() => setOpen(true)}>Registrar no dente {tooth}</Button>
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={`Dente ${tooth}`}
        footer={
          <>
            <Button data-modal-cancel variant="outline" onClick={() => setOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={() => add.mutate(undefined)} loading={add.isPending}>
              Registrar
            </Button>
          </>
        }
      >
        <FormGrid $columns={2}>
          <SelectField
            label="Condição"
            value={condition}
            onChange={(e) => setCondition(e.target.value)}
            options={TOOTH_CONDITIONS.map((c) => ({ value: c.value, label: c.label }))}
          />
          <SelectField
            label="Situação"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            options={Object.entries(CHART_STATUS).map(([value, label]) => ({ value, label }))}
          />
          <FullRow>
            <fieldset style={{ border: "none", padding: 0, margin: 0 }}>
              <legend style={{ fontWeight: 600, marginBottom: 8 }}>Faces (deixe em branco para o dente inteiro)</legend>
              <Row $gap={4}>
                {FACES.map((f) => (
                  <CheckboxField
                    key={f.value}
                    label={f.label}
                    checked={faces.includes(f.value)}
                    onChange={() => toggle(f.value)}
                  />
                ))}
              </Row>
            </fieldset>
          </FullRow>
          <FullRow>
            <TextAreaField
              label="Observações"
              rows={2}
              maxLength={500}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </FullRow>
          {add.error && (
            <FullRow>
              <Alert tone="danger">{add.error.message}</Alert>
            </FullRow>
          )}
        </FormGrid>
      </Modal>
    </>
  );
}

const ConditionCell = styled.span<{ $voided: boolean }>`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  text-decoration: ${({ $voided }) => ($voided ? "line-through" : "none")};
`;

const Swatch = styled.span`
  width: 12px;
  height: 12px;
  border-radius: 3px;
`;
