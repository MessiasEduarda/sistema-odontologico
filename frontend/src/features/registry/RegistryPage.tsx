"use client";

import { useQuery } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Card, Muted, PageHeader, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow, SelectField } from "@/components/ui/fields";
import { IconButton } from "@/components/ui/IconButton";
import { LoadingState } from "@/components/ui/LoadingState";
import { Modal } from "@/components/ui/Modal";
import { Tabs } from "@/components/ui/Tabs";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { Procedure, Professional, Room, ScheduleSlot } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";
import { UF_OPTIONS, WEEKDAYS } from "@/lib/labels";
import { useAction } from "@/lib/mutations";

export function RegistryPage() {
  const [tab, setTab] = useState("profissionais");
  return (
    <Stack>
      <PageHeader
        title="Cadastros"
        description="Profissionais, procedimentos e salas. Fichas de anamnese, termos e orientações têm páginas próprias no menu."
      />
      <Tabs
        label="Cadastros"
        active={tab}
        onChange={setTab}
        items={[
          { id: "profissionais", label: "Profissionais", content: <ProfessionalsTab /> },
          { id: "procedimentos", label: "Procedimentos", content: <ProceduresTab /> },
          { id: "salas", label: "Salas", content: <RoomsTab /> },
        ]}
      />
    </Stack>
  );
}

/* ------------------------------------------------------------------ Profissionais */

function ProfessionalsTab() {
  const [editing, setEditing] = useState<Professional | "new" | null>(null);
  const [scheduling, setScheduling] = useState<Professional | null>(null);
  const list = useQuery({ queryKey: ["professionals", "all"], queryFn: () => api.clinic.professionals() });
  return (
    <Stack $gap={4}>
      <Row $justify="flex-end">
        <Button onClick={() => setEditing("new")}>
          <Plus size={16} aria-hidden /> Novo profissional
        </Button>
      </Row>
      <DataTable<Professional>
        caption="Profissionais"
        rows={list.data}
        loading={list.isPending}
        error={list.error}
        onRetry={list.refetch}
        rowKey={(p) => p.id}
        emptyTitle="Nenhum profissional cadastrado"
        columns={[
          {
            key: "name",
            header: "Nome",
            render: (p) => (
              <NameCell>
                <Dot style={{ background: p.color }} aria-hidden />
                <strong>{p.name}</strong>
              </NameCell>
            ),
          },
          { key: "cro", header: "CRO", render: (p) => (p.cro ? `CRO-${p.croState ?? ""} ${p.cro}` : "—") },
          { key: "spec", header: "Especialidade", hideOnMobile: true, render: (p) => p.specialty ?? "—" },
          {
            key: "status",
            header: "Situação",
            render: (p) => (p.active ? <Badge $tone="success">Ativo</Badge> : <Badge>Inativo</Badge>),
          },
          {
            key: "actions",
            header: "",
            render: (p) => (
              <Row $gap={2} $wrap={false}>
                <Button variant="ghost" onClick={() => setScheduling(p)}>
                  Horários
                </Button>
                <Button variant="ghost" onClick={() => setEditing(p)}>
                  Editar
                </Button>
              </Row>
            ),
          },
        ]}
      />
      {editing && (
        <ProfessionalForm professional={editing === "new" ? null : editing} onClose={() => setEditing(null)} />
      )}
      {scheduling && <ScheduleEditor professional={scheduling} onClose={() => setScheduling(null)} />}
    </Stack>
  );
}

function ProfessionalForm({ professional, onClose }: { professional: Professional | null; onClose: () => void }) {
  const members = useQuery({ queryKey: ["members"], queryFn: api.clinic.members, retry: false });
  const [v, setV] = useState({
    userId: professional?.userId ?? "",
    name: professional?.name ?? "",
    cro: professional?.cro ?? "",
    croState: professional?.croState ?? "",
    specialty: professional?.specialty ?? "",
    color: professional?.color ?? "#9C7A5B",
    phone: professional?.phone ?? "",
    email: professional?.email ?? "",
    commissionPercent: professional?.commissionPercent ?? "",
    active: professional?.active ?? true,
  });
  const save = useAction(
    () => {
      const body = {
        ...v,
        userId: v.userId || null,
        cro: v.cro || null,
        croState: v.croState || null,
        specialty: v.specialty || null,
        phone: v.phone || null,
        email: v.email || null,
        commissionPercent: v.commissionPercent === "" ? null : Number(v.commissionPercent),
        version: professional?.version,
      };
      return professional ? api.clinic.updateProfessional(professional.id, body) : api.clinic.createProfessional(body);
    },
    {
      success: "Profissional salvo com sucesso!",
      invalidate: [["professionals"]],
      onSuccess: onClose,
    },
  );
  return (
    <Modal
      open
      onClose={onClose}
      title={professional ? "Editar profissional" : "Novo profissional"}
      size="lg"
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
      <FormGrid $columns={3}>
        <FullRow>
          <TextField
            label="Nome de exibição"
            required
            maxLength={160}
            value={v.name}
            onChange={(e) => setV({ ...v, name: e.target.value })}
          />
        </FullRow>
        <TextField label="CRO" maxLength={20} value={v.cro} onChange={(e) => setV({ ...v, cro: e.target.value })} />
        <SelectField
          label="UF do CRO"
          placeholder="—"
          value={v.croState}
          onChange={(e) => setV({ ...v, croState: e.target.value })}
          options={UF_OPTIONS}
        />
        <TextField
          label="Especialidade"
          maxLength={80}
          value={v.specialty}
          onChange={(e) => setV({ ...v, specialty: e.target.value })}
        />
        <TextField
          label="Telefone"
          maxLength={20}
          value={v.phone}
          onChange={(e) => setV({ ...v, phone: e.target.value })}
        />
        <TextField
          label="E-mail"
          type="email"
          maxLength={254}
          value={v.email}
          onChange={(e) => setV({ ...v, email: e.target.value })}
        />
        <TextField
          label="Comissão (%)"
          type="number"
          min={0}
          max={100}
          step="0.01"
          value={v.commissionPercent ?? ""}
          onChange={(e) => setV({ ...v, commissionPercent: e.target.value })}
        />
        <div>
          <ColorLabel htmlFor="prof-color">Cor na agenda</ColorLabel>
          <input id="prof-color" type="color" value={v.color} onChange={(e) => setV({ ...v, color: e.target.value })} />
        </div>
        <FullRow>
          <SelectField
            label="Usuário do sistema (opcional)"
            hint="Vincule à conta da pessoa na equipe, se ela também acessa o sistema."
            placeholder="(sem vínculo)"
            value={v.userId}
            onChange={(e) => setV({ ...v, userId: e.target.value })}
            options={(members.data ?? []).map((m) => ({ value: m.userId, label: `${m.name} (${m.email})` }))}
          />
        </FullRow>
        <CheckboxField
          label="Ativo (aparece na agenda)"
          checked={v.active}
          onChange={(e) => setV({ ...v, active: e.target.checked })}
        />
        {save.error && (
          <FullRow>
            <Alert tone="danger">{save.error.message}</Alert>
          </FullRow>
        )}
      </FormGrid>
    </Modal>
  );
}

function ScheduleEditor({ professional, onClose }: { professional: Professional; onClose: () => void }) {
  const current = useQuery({
    queryKey: ["schedule", professional.id],
    queryFn: () => api.clinic.schedule(professional.id),
  });
  const [slots, setSlots] = useState<ScheduleSlot[] | null>(null);
  const list =
    slots ??
    (current.data ?? []).map((s) => ({ ...s, startTime: s.startTime.slice(0, 5), endTime: s.endTime.slice(0, 5) }));
  const set = (next: ScheduleSlot[]) => setSlots(next);
  const save = useAction(() => api.clinic.saveSchedule(professional.id, list), {
    success: "Horários salvos com sucesso!",
    invalidate: [["schedule", professional.id], ["availability"]],
    onSuccess: onClose,
  });
  const fillWeekdays = () => {
    const next: ScheduleSlot[] = [];
    for (let d = 1; d <= 5; d++) {
      next.push(
        { weekday: d, startTime: "08:00", endTime: "12:00" },
        { weekday: d, startTime: "13:00", endTime: "18:00" },
      );
    }
    set(next);
  };
  return (
    <Modal
      open
      onClose={onClose}
      title={`Horários de atendimento: ${professional.name}`}
      size="lg"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => save.mutate(undefined)} loading={save.isPending}>
            Salvar horários
          </Button>
        </>
      }
    >
      {current.isPending ? (
        <LoadingState />
      ) : (
        <Stack $gap={3}>
          <Muted>
            Usados para sugerir horários livres e calcular a ocupação. Agendar fora deles continua possível.
          </Muted>
          {list.map((s, i) => (
            <SlotRow key={i}>
              <SelectField
                label="Dia"
                value={String(s.weekday)}
                onChange={(e) => set(list.map((x, j) => (j === i ? { ...x, weekday: Number(e.target.value) } : x)))}
                options={WEEKDAYS.slice(1).map((d, idx) => ({ value: String(idx + 1), label: d }))}
              />
              <TextField
                label="Início"
                type="time"
                value={s.startTime}
                onChange={(e) => set(list.map((x, j) => (j === i ? { ...x, startTime: e.target.value } : x)))}
              />
              <TextField
                label="Fim"
                type="time"
                value={s.endTime}
                onChange={(e) => set(list.map((x, j) => (j === i ? { ...x, endTime: e.target.value } : x)))}
              />
              <IconButton
                icon={Trash2}
                label="Remover horário"
                tone="danger"
                onClick={() => set(list.filter((_, j) => j !== i))}
              />
            </SlotRow>
          ))}
          <Row>
            <Button
              variant="outline"
              onClick={() => set([...list, { weekday: 1, startTime: "08:00", endTime: "12:00" }])}
            >
              <Plus size={16} aria-hidden /> Adicionar horário
            </Button>
            <Button variant="ghost" onClick={fillWeekdays}>
              Preencher seg–sex 8h–12h e 13h–18h
            </Button>
          </Row>
          {save.error && <Alert tone="danger">{save.error.message}</Alert>}
        </Stack>
      )}
    </Modal>
  );
}

/* ------------------------------------------------------------------ Procedimentos */

function ProceduresTab() {
  const [editing, setEditing] = useState<Procedure | "new" | null>(null);
  const list = useQuery({ queryKey: ["procedures", "all"], queryFn: () => api.clinic.procedures() });
  return (
    <Stack $gap={4}>
      <Row $justify="flex-end">
        <Button onClick={() => setEditing("new")}>
          <Plus size={16} aria-hidden /> Novo procedimento
        </Button>
      </Row>
      <DataTable<Procedure>
        caption="Procedimentos"
        rows={list.data}
        loading={list.isPending}
        rowKey={(p) => p.id}
        onRowClick={setEditing}
        emptyTitle="Nenhum procedimento"
        columns={[
          { key: "code", header: "Código", render: (p) => p.code ?? "—" },
          { key: "name", header: "Procedimento", render: (p) => <strong>{p.name}</strong> },
          { key: "cat", header: "Categoria", hideOnMobile: true, render: (p) => p.category ?? "—" },
          { key: "price", header: "Valor", align: "right", render: (p) => formatMoney(p.defaultPrice) },
          { key: "dur", header: "Duração", align: "right", render: (p) => `${p.durationMinutes} min` },
          {
            key: "status",
            header: "Situação",
            render: (p) => (p.active ? <Badge $tone="success">Ativo</Badge> : <Badge>Inativo</Badge>),
          },
        ]}
      />
      {editing && <ProcedureForm procedure={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </Stack>
  );
}

function ProcedureForm({ procedure, onClose }: { procedure: Procedure | null; onClose: () => void }) {
  const [v, setV] = useState({
    code: procedure?.code ?? "",
    name: procedure?.name ?? "",
    category: procedure?.category ?? "",
    defaultPrice: Number(procedure?.defaultPrice ?? 0),
    durationMinutes: procedure?.durationMinutes ?? 30,
    active: procedure?.active ?? true,
  });
  const save = useAction(
    () => {
      const body = { ...v, code: v.code || null, category: v.category || null };
      return procedure ? api.clinic.updateProcedure(procedure.id, body) : api.clinic.createProcedure(body);
    },
    {
      success: "Procedimento salvo com sucesso!",
      invalidate: [["procedures"]],
      onSuccess: onClose,
    },
  );
  return (
    <Modal
      open
      onClose={onClose}
      title={procedure ? "Editar procedimento" : "Novo procedimento"}
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
          label="Código (opcional)"
          maxLength={20}
          value={v.code}
          onChange={(e) => setV({ ...v, code: e.target.value })}
        />
        <TextField
          label="Categoria"
          maxLength={60}
          value={v.category}
          onChange={(e) => setV({ ...v, category: e.target.value })}
        />
        <TextField
          label="Valor de tabela (R$)"
          type="number"
          min={0}
          step="0.01"
          value={v.defaultPrice}
          onChange={(e) => setV({ ...v, defaultPrice: Number(e.target.value) })}
        />
        <TextField
          label="Duração (min)"
          type="number"
          min={5}
          max={480}
          step={5}
          value={v.durationMinutes}
          onChange={(e) => setV({ ...v, durationMinutes: Number(e.target.value) })}
        />
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

/* ------------------------------------------------------------------ Salas */

function RoomsTab() {
  const rooms = useQuery({ queryKey: ["rooms"], queryFn: api.clinic.rooms });
  const [name, setName] = useState("");
  const create = useAction(() => api.clinic.createRoom({ name, active: true }), {
    success: "Sala criada com sucesso!",
    invalidate: [["rooms"]],
    onSuccess: () => setName(""),
  });
  const toggle = useAction((r: Room) => api.clinic.updateRoom(r.id, { name: r.name, active: !r.active }), {
    success: "Sala atualizada com sucesso!",
    invalidate: [["rooms"]],
  });
  return (
    <Stack $gap={4}>
      <Card>
        <Row $gap={3}>
          <div style={{ flex: 1, minWidth: 220 }}>
            <TextField
              label="Nova sala"
              maxLength={80}
              placeholder="Ex.: Consultório 3"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <Button onClick={() => create.mutate(undefined)} loading={create.isPending} disabled={!name.trim()}>
            Adicionar
          </Button>
        </Row>
        {create.error && <Alert tone="danger">{create.error.message}</Alert>}
      </Card>
      <DataTable<Room>
        caption="Salas"
        rows={rooms.data}
        loading={rooms.isPending}
        rowKey={(r) => r.id}
        emptyTitle="Nenhuma sala"
        columns={[
          { key: "name", header: "Sala", render: (r) => <strong>{r.name}</strong> },
          {
            key: "status",
            header: "Situação",
            render: (r) => (r.active ? <Badge $tone="success">Ativa</Badge> : <Badge>Inativa</Badge>),
          },
          {
            key: "actions",
            header: "",
            render: (r) => (
              <Button variant="ghost" onClick={() => toggle.mutate(r)}>
                {r.active ? "Desativar" : "Reativar"}
              </Button>
            ),
          },
        ]}
      />
    </Stack>
  );
}

const NameCell = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 8px;
`;

const Dot = styled.span`
  width: 12px;
  height: 12px;
  border-radius: 50%;
`;

const ColorLabel = styled.label`
  display: block;
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  margin-bottom: ${({ theme }) => theme.space[2]};
`;

const SlotRow = styled.div`
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr auto;
  gap: ${({ theme }) => theme.space[3]};
  align-items: end;
`;
