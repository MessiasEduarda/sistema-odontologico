"use client";

import { useQuery } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import { useId, useState } from "react";
import styled from "styled-components";
import { PatientSelect } from "@/components/domain/PatientSelect";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Muted, Stack } from "@/components/ui/display";
import { FormGrid, FullRow, SelectField, TextAreaField } from "@/components/ui/fields";
import { IconButton } from "@/components/ui/IconButton";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { AestheticRegion } from "@/lib/api/types";
import { localDateTimeToIso, toDateInput, toTimeInput } from "@/lib/format";
import { AESTHETIC_TYPES, AESTHETIC_UNITS, FACE_REGIONS } from "@/lib/labels";
import { useAction } from "@/lib/mutations";

const TYPE_OPTIONS = Object.entries(AESTHETIC_TYPES).map(([value, label]) => ({ value, label }));
const UNIT_OPTIONS = Object.entries(AESTHETIC_UNITS).map(([value, label]) => ({ value, label }));
const DEFAULT_UNIT: Record<string, string> = { TOXIN: "U", FILLER: "ML", BIOSTIMULATOR: "ML", SKINBOOSTER: "ML" };

/**
 * Registro de aplicação de harmonização orofacial: produto, lote e validade (rastreabilidade), regiões tratadas com
 * quantidade, técnica, termo assinado e data de retorno. Depois de salvo não é editado (só anulado com motivo).
 */
export function AestheticForm({
  patient: fixedPatient,
  onClose,
}: {
  patient?: { id: string; name: string };
  onClose: () => void;
}) {
  const regionsListId = useId();
  const now = new Date();
  const [patient, setPatient] = useState<{ id: string; name: string } | null>(fixedPatient ?? null);
  const [v, setV] = useState({
    professionalId: "",
    date: toDateInput(now),
    time: toTimeInput(now),
    procedureType: "TOXIN",
    productName: "",
    manufacturer: "",
    anvisaRegistry: "",
    batch: "",
    expiryDate: "",
    unit: "U",
    technique: "",
    notes: "",
    returnDate: "",
    consentId: "",
  });
  const [regions, setRegions] = useState<AestheticRegion[]>([{ region: "", quantity: 0 }]);
  const professionals = useQuery({ queryKey: ["professionals", true], queryFn: () => api.clinic.professionals(true) });
  const consents = useQuery({
    queryKey: ["consents", patient?.id],
    queryFn: () => api.clinical.consents(patient?.id ?? ""),
    enabled: Boolean(patient),
  });
  const total = regions.reduce((sum, r) => sum + (Number(r.quantity) || 0), 0);

  const save = useAction(
    () =>
      api.clinical.addAesthetic(patient?.id ?? "", {
        professionalId: v.professionalId,
        performedAt: localDateTimeToIso(v.date, v.time),
        procedureType: v.procedureType,
        productName: v.productName,
        manufacturer: v.manufacturer || null,
        anvisaRegistry: v.anvisaRegistry || null,
        batch: v.batch,
        expiryDate: v.expiryDate || null,
        unit: v.unit,
        regions: regions.map((r) => ({ ...r, quantity: Number(r.quantity), notes: r.notes || null })),
        technique: v.technique || null,
        notes: v.notes || null,
        returnDate: v.returnDate || null,
        consentId: v.consentId || null,
      }),
    {
      success: "Aplicação registrada com sucesso!",
      invalidate: [["aesthetics"], ["aesthetics-search"]],
      onSuccess: onClose,
    },
  );

  const setRegion = (i: number, patch: Partial<AestheticRegion>) =>
    setRegions(regions.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  const invalid =
    !patient ||
    !v.professionalId ||
    !v.productName.trim() ||
    !v.batch.trim() ||
    regions.length === 0 ||
    regions.some((r) => !r.region.trim() || !(Number(r.quantity) > 0));
  const expired = v.expiryDate !== "" && v.expiryDate < v.date;
  const signedConsents = (consents.data ?? []).filter((c) => c.status !== "REVOKED");

  return (
    <Modal
      open
      onClose={onClose}
      title="Registrar aplicação"
      size="lg"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => save.mutate(undefined)} loading={save.isPending} disabled={invalid || expired}>
            Registrar
          </Button>
        </>
      }
    >
      <Stack $gap={4}>
        <FormGrid $columns={3}>
          <FullRow>
            {fixedPatient ? (
              <TextField label="Paciente" value={fixedPatient.name} disabled readOnly />
            ) : (
              <PatientSelect value={patient} onChange={setPatient} required />
            )}
          </FullRow>
          <SelectField
            label="Profissional"
            required
            placeholder="Selecione…"
            value={v.professionalId}
            onChange={(e) => setV({ ...v, professionalId: e.target.value })}
            options={(professionals.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
          />
          <TextField
            label="Data"
            type="date"
            required
            value={v.date}
            onChange={(e) => setV({ ...v, date: e.target.value })}
          />
          <TextField
            label="Hora"
            type="time"
            required
            value={v.time}
            onChange={(e) => setV({ ...v, time: e.target.value })}
          />
          <SelectField
            label="Procedimento"
            value={v.procedureType}
            onChange={(e) =>
              setV({ ...v, procedureType: e.target.value, unit: DEFAULT_UNIT[e.target.value] ?? v.unit })
            }
            options={TYPE_OPTIONS}
          />
          <TextField
            label="Produto (nome comercial)"
            required
            maxLength={160}
            value={v.productName}
            onChange={(e) => setV({ ...v, productName: e.target.value })}
          />
          <TextField
            label="Fabricante"
            maxLength={160}
            value={v.manufacturer}
            onChange={(e) => setV({ ...v, manufacturer: e.target.value })}
          />
          <TextField
            label="Lote"
            required
            maxLength={60}
            value={v.batch}
            onChange={(e) => setV({ ...v, batch: e.target.value })}
            hint="Usado para localizar pacientes de um mesmo lote."
          />
          <TextField
            label="Validade do produto"
            type="date"
            value={v.expiryDate}
            error={expired ? "Produto vencido na data da aplicação." : undefined}
            onChange={(e) => setV({ ...v, expiryDate: e.target.value })}
          />
          <TextField
            label="Registro ANVISA"
            maxLength={40}
            value={v.anvisaRegistry}
            onChange={(e) => setV({ ...v, anvisaRegistry: e.target.value })}
          />
        </FormGrid>

        <div>
          <SectionTitle>Regiões e quantidades</SectionTitle>
          <Muted>Escolha da lista ou digite outra região.</Muted>
          <datalist id={regionsListId}>
            {FACE_REGIONS.map((r) => (
              <option key={r} value={r} />
            ))}
          </datalist>
          <Stack $gap={2} style={{ marginTop: 8 }}>
            {regions.map((r, i) => (
              <RegionRow key={i}>
                <TextField
                  label={`Região ${i + 1}`}
                  list={regionsListId}
                  maxLength={80}
                  value={r.region}
                  onChange={(e) => setRegion(i, { region: e.target.value })}
                />
                <TextField
                  label={`Quantidade (${AESTHETIC_UNITS[v.unit]})`}
                  type="number"
                  min={0}
                  step="0.01"
                  value={r.quantity || ""}
                  onChange={(e) => setRegion(i, { quantity: Number(e.target.value) })}
                />
                <TextField
                  label="Observação"
                  maxLength={300}
                  value={r.notes ?? ""}
                  onChange={(e) => setRegion(i, { notes: e.target.value })}
                />
                <IconButton
                  icon={Trash2}
                  label="Remover região"
                  tone="danger"
                  disabled={regions.length === 1}
                  onClick={() => setRegions(regions.filter((_, j) => j !== i))}
                />
              </RegionRow>
            ))}
          </Stack>
          <RegionFooter>
            <Button variant="outline" onClick={() => setRegions([...regions, { region: "", quantity: 0 }])}>
              <Plus size={16} aria-hidden /> Adicionar região
            </Button>
            <SelectField
              label="Unidade"
              value={v.unit}
              onChange={(e) => setV({ ...v, unit: e.target.value })}
              options={UNIT_OPTIONS}
            />
            <strong>
              Total: {total.toLocaleString("pt-BR")} {AESTHETIC_UNITS[v.unit]}
            </strong>
          </RegionFooter>
        </div>

        <FormGrid $columns={2}>
          <FullRow>
            <TextAreaField
              label="Técnica / plano de aplicação"
              rows={2}
              maxLength={2000}
              value={v.technique}
              onChange={(e) => setV({ ...v, technique: e.target.value })}
            />
          </FullRow>
          <FullRow>
            <TextAreaField
              label="Observações"
              rows={2}
              maxLength={2000}
              value={v.notes}
              onChange={(e) => setV({ ...v, notes: e.target.value })}
            />
          </FullRow>
          <TextField
            label="Retorno / revisão em"
            type="date"
            value={v.returnDate}
            onChange={(e) => setV({ ...v, returnDate: e.target.value })}
          />
          <SelectField
            label="Termo de consentimento"
            placeholder={patient ? "(nenhum)" : "Escolha o paciente primeiro"}
            value={v.consentId}
            onChange={(e) => setV({ ...v, consentId: e.target.value })}
            options={signedConsents.map((c) => ({
              value: c.id,
              label: `${c.title} — ${c.status === "SIGNED" ? "assinado" : "aguardando assinatura"}`,
            }))}
          />
        </FormGrid>
        {patient && signedConsents.length === 0 && consents.data && (
          <Alert tone="warning">
            Este paciente não tem termo emitido. Emita o termo na página Termos antes do procedimento.
          </Alert>
        )}
        {save.error && <Alert tone="danger">{save.error.message}</Alert>}
      </Stack>
    </Modal>
  );
}

const SectionTitle = styled.h3`
  font-size: ${({ theme }) => theme.fontSizes.base};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  color: ${({ theme }) => theme.colors.primaryText};
`;

const RegionRow = styled.div`
  display: grid;
  grid-template-columns: 1fr;
  gap: ${({ theme }) => theme.space[2]};
  align-items: end;

  @media (min-width: 640px) {
    grid-template-columns: 2fr 1fr 2fr auto;
  }
`;

const RegionFooter = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: ${({ theme }) => theme.space[4]};
  margin-top: ${({ theme }) => theme.space[3]};

  > div {
    width: 180px;
  }
`;
