"use client";

import { useQuery } from "@tanstack/react-query";
import {
  addDays,
  addMonths,
  addWeeks,
  eachDayOfInterval,
  endOfMonth,
  endOfWeek,
  format,
  isSameDay,
  isSameMonth,
  startOfDay,
  startOfMonth,
  startOfWeek,
} from "date-fns";
import { ptBR } from "date-fns/locale";
import { CalendarOff, CalendarPlus, ChevronLeft, ChevronRight, Hourglass } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useMemo, useState, useSyncExternalStore, type MouseEvent } from "react";
import styled from "styled-components";
import { Button } from "@/components/ui/Button";
import { Badge, Card, ErrorState, PageHeader, Stack } from "@/components/ui/display";
import { SelectField } from "@/components/ui/fields";
import { IconButton } from "@/components/ui/IconButton";
import { LoadingState } from "@/components/ui/LoadingState";
import { api } from "@/lib/api/endpoints";
import type { Appointment, Professional, ScheduleBlock, WaitlistEntry } from "@/lib/api/types";
import { formatTime, toDateInput, toTimeInput } from "@/lib/format";
import { APPOINTMENT_STATUS } from "@/lib/labels";
import { useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { AppointmentDetail } from "./AppointmentDetail";
import { AppointmentForm, draftFrom, type AppointmentDraft } from "./AppointmentForm";
import { BlockForm, RemoveBlock, useScheduleBlocks } from "./ScheduleBlocks";
import { useWaitlistCount, WaitlistPanel } from "./Waitlist";

type View = "day" | "week" | "month" | "list";

const START_HOUR = 7;
const END_HOUR = 21;
const PX_PER_MIN = 1.1;
const VIEWS: { id: View; label: string }[] = [
  { id: "day", label: "Dia" },
  { id: "week", label: "Semana" },
  { id: "month", label: "Mês" },
  { id: "list", label: "Lista" },
];

const NARROW_QUERY = "(max-width: 767px)";

function subscribeNarrow(onChange: () => void) {
  const query = window.matchMedia(NARROW_QUERY);
  query.addEventListener("change", onChange);
  return () => query.removeEventListener("change", onChange);
}

function isNarrowScreen() {
  return window.matchMedia(NARROW_QUERY).matches;
}

export function AgendaPage() {
  const params = useSearchParams();
  const can = useCan();
  // Sem escolha da pessoa, a visão segue a tela (lista no celular). Decidido após a hidratação, para o HTML do
  // servidor e o do navegador serem iguais.
  const [chosenView, setView] = useState<View | null>(null);
  const isNarrow = useSyncExternalStore(subscribeNarrow, isNarrowScreen, () => false);
  const view: View = chosenView ?? (isNarrow ? "list" : "day");
  const [date, setDate] = useState(() => (params.get("dia") === "amanha" ? addDays(new Date(), 1) : new Date()));
  const [professionalId, setProfessionalId] = useState("");
  const [detailId, setDetailId] = useState<string | null>(null);
  const [blocking, setBlocking] = useState(false);
  const [removingBlock, setRemovingBlock] = useState<ScheduleBlock | null>(null);
  const [waitlistOpen, setWaitlistOpen] = useState(false);
  const waitingCount = useWaitlistCount();
  const [form, setForm] = useState<{
    draft: AppointmentDraft;
    editing: Appointment | null;
    /** Agendamento feito a partir da lista de espera: a entrada sai da lista ao salvar. */
    fromWaitlist?: WaitlistEntry;
  } | null>(() => (params.get("novo") ? { draft: emptyDraft(new Date(), "", 30), editing: null } : null));

  const range = useMemo(() => rangeFor(view, date), [view, date]);
  const professionals = useQuery({ queryKey: ["professionals", true], queryFn: () => api.clinic.professionals(true) });
  const appointments = useQuery({
    queryKey: ["appointments", "range", range.from.toISOString(), range.to.toISOString(), professionalId],
    queryFn: () =>
      api.appointments.list({
        from: range.from.toISOString(),
        to: range.to.toISOString(),
        professionalId: professionalId || undefined,
      }),
  });

  const blocks = useScheduleBlocks(range.from, range.to);
  const visibleBlocks = (blocks.data ?? []).filter(
    (b) => !professionalId || !b.professionalId || b.professionalId === professionalId,
  );
  const visible = (appointments.data ?? []).filter((a) => a.status !== "CANCELLED");
  const columnsForDay: Professional[] = (professionals.data ?? []).filter(
    (p) => !professionalId || p.id === professionalId,
  );

  const openNew = (start: Date, profId?: string) => {
    if (!can(P.SCHEDULE_WRITE)) return;
    setForm({ draft: emptyDraft(start, profId ?? professionalId, 30), editing: null });
  };

  const scheduleFromWaitlist = (entry: WaitlistEntry) => {
    setWaitlistOpen(false);
    setForm({
      draft: {
        ...emptyDraft(roundUp(new Date()), entry.professionalId ?? professionalId, 30),
        patient: { id: entry.patientId, name: entry.patientName },
        procedureId: entry.procedureId ?? "",
        notes: entry.preference ? `Lista de espera — preferência: ${entry.preference}` : "",
      },
      editing: null,
      fromWaitlist: entry,
    });
  };

  const move = (dir: 1 | -1) =>
    setDate((d) => (view === "month" ? addMonths(d, dir) : view === "week" ? addWeeks(d, dir) : addDays(d, dir)));

  return (
    <Stack>
      <PageHeader
        title="Agenda"
        actions={
          can(P.SCHEDULE_WRITE) && (
            <>
              <Button variant="outline" onClick={() => setWaitlistOpen(true)}>
                <Hourglass size={18} aria-hidden /> Lista de espera
                {waitingCount > 0 && <Badge $tone="primary">{waitingCount}</Badge>}
              </Button>
              <Button variant="outline" onClick={() => setBlocking(true)}>
                <CalendarOff size={18} aria-hidden /> Bloquear horário
              </Button>
              <Button onClick={() => openNew(roundUp(new Date()))}>
                <CalendarPlus size={18} aria-hidden /> Novo agendamento
              </Button>
            </>
          )
        }
      />

      <Toolbar>
        <Nav>
          <IconButton icon={ChevronLeft} label="Período anterior" onClick={() => move(-1)} />
          <Button variant="outline" onClick={() => setDate(new Date())}>
            Hoje
          </Button>
          <IconButton icon={ChevronRight} label="Próximo período" onClick={() => move(1)} />
          <PeriodLabel aria-live="polite">{range.label}</PeriodLabel>
        </Nav>
        <Filters>
          <ViewSwitch role="group" aria-label="Visualização">
            {VIEWS.map((v) => (
              <ViewButton key={v.id} type="button" aria-pressed={view === v.id} onClick={() => setView(v.id)}>
                {v.label}
              </ViewButton>
            ))}
          </ViewSwitch>
          <ProfessionalFilter>
            <SelectField
              label="Profissional"
              value={professionalId}
              onChange={(e) => setProfessionalId(e.target.value)}
              placeholder="Todos"
              options={(professionals.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
            />
          </ProfessionalFilter>
        </Filters>
      </Toolbar>

      {appointments.error ? (
        <ErrorState error={appointments.error} onRetry={appointments.refetch} />
      ) : appointments.isPending || professionals.isPending ? (
        <LoadingState />
      ) : view === "month" ? (
        <MonthView
          date={date}
          appointments={visible}
          onDay={(d) => {
            setDate(d);
            setView("day");
          }}
          onOpen={setDetailId}
        />
      ) : view === "list" ? (
        <ListView
          appointments={visible}
          blocks={visibleBlocks}
          days={range.days}
          onOpen={setDetailId}
          onBlock={can(P.SCHEDULE_WRITE) ? setRemovingBlock : undefined}
        />
      ) : (
        <TimeGrid
          columns={
            view === "day"
              ? columnsForDay.map((p) => ({
                  key: p.id,
                  label: p.name,
                  day: date,
                  professionalId: p.id,
                  color: p.color,
                }))
              : range.days.map((d) => ({
                  key: d.toISOString(),
                  label: format(d, "EEE dd/MM", { locale: ptBR }),
                  day: d,
                  professionalId: professionalId || undefined,
                  color: undefined,
                }))
          }
          appointments={visible}
          blocks={visibleBlocks}
          onOpen={setDetailId}
          onCreate={openNew}
          onBlock={can(P.SCHEDULE_WRITE) ? setRemovingBlock : undefined}
        />
      )}

      {(appointments.data?.length ?? 0) > visible.length && (
        <Hint>
          {(appointments.data?.length ?? 0) - visible.length} atendimento(s) cancelado(s) oculto(s) neste período.
        </Hint>
      )}

      {blocking && <BlockForm initialDate={date} onClose={() => setBlocking(false)} />}
      {removingBlock && <RemoveBlock block={removingBlock} onClose={() => setRemovingBlock(null)} />}
      {waitlistOpen && <WaitlistPanel onClose={() => setWaitlistOpen(false)} onSchedule={scheduleFromWaitlist} />}
      {form && (
        <AppointmentForm
          key={form.editing?.id ?? form.fromWaitlist?.id ?? "new"}
          open
          initial={form.draft}
          editing={form.editing}
          onClose={() => setForm(null)}
          afterSave={waitlistResolver(form.fromWaitlist)}
        />
      )}
      <AppointmentDetail
        appointmentId={detailId}
        onClose={() => setDetailId(null)}
        onEdit={(a) => {
          setDetailId(null);
          setForm({ draft: draftFrom(a), editing: a });
        }}
      />
    </Stack>
  );
}

function waitlistResolver(entry: WaitlistEntry | undefined) {
  return entry ? (a: Appointment) => api.waitlist.resolve(entry.id, "SCHEDULED", a.id) : undefined;
}

function emptyDraft(start: Date, professionalId: string, duration: number): AppointmentDraft {
  return {
    patient: null,
    professionalId,
    roomId: "",
    procedureId: "",
    date: toDateInput(start),
    time: toTimeInput(start),
    duration,
    notes: "",
  };
}

function roundUp(d: Date): Date {
  const r = new Date(d);
  r.setMinutes(Math.ceil(r.getMinutes() / 15) * 15, 0, 0);
  return r;
}

function rangeFor(view: View, date: Date) {
  if (view === "month") {
    const from = startOfWeek(startOfMonth(date), { weekStartsOn: 1 });
    const to = addDays(endOfWeek(endOfMonth(date), { weekStartsOn: 1 }), 1);
    return {
      from,
      to,
      days: eachDayOfInterval({ start: from, end: addDays(to, -1) }),
      label: format(date, "MMMM 'de' yyyy", { locale: ptBR }),
    };
  }
  if (view === "week") {
    const from = startOfWeek(date, { weekStartsOn: 1 });
    const to = addDays(from, 7);
    return {
      from,
      to,
      days: eachDayOfInterval({ start: from, end: addDays(to, -1) }),
      label: `${format(from, "dd/MM")} – ${format(addDays(to, -1), "dd/MM/yyyy")}`,
    };
  }
  const from = startOfDay(date);
  return {
    from,
    to: addDays(from, 1),
    days: [from],
    label: format(date, "EEEE, dd 'de' MMMM", { locale: ptBR }),
  };
}

/* ------------------------------------------------------------------ Grade de horários (dia/semana) */

interface Column {
  key: string;
  label: string;
  day: Date;
  professionalId?: string;
  color?: string;
}

/** Parte de um bloqueio que cai num dia, recortada ao horário visível da grade. */
function blockSegment(block: ScheduleBlock, day: Date) {
  const gridStart = new Date(day);
  gridStart.setHours(START_HOUR, 0, 0, 0);
  const gridEnd = new Date(day);
  gridEnd.setHours(END_HOUR, 0, 0, 0);
  const start = new Date(Math.max(new Date(block.startsAt).getTime(), gridStart.getTime()));
  const end = new Date(Math.min(new Date(block.endsAt).getTime(), gridEnd.getTime()));
  return end > start ? { start, end } : null;
}

function TimeGrid({
  columns,
  appointments,
  blocks,
  onOpen,
  onCreate,
  onBlock,
}: {
  columns: Column[];
  appointments: Appointment[];
  blocks: ScheduleBlock[];
  onOpen: (id: string) => void;
  onCreate: (start: Date, professionalId?: string) => void;
  onBlock?: (block: ScheduleBlock) => void;
}) {
  const hours = Array.from({ length: END_HOUR - START_HOUR }, (_, i) => START_HOUR + i);
  const height = (END_HOUR - START_HOUR) * 60 * PX_PER_MIN;

  if (columns.length === 0) {
    return (
      <Card>
        <Hint>Cadastre profissionais em Cadastros para usar a agenda.</Hint>
      </Card>
    );
  }

  const clickSlot = (e: MouseEvent<HTMLDivElement>, col: Column) => {
    if (e.target !== e.currentTarget) return;
    const y = e.nativeEvent.offsetY;
    const minutes = Math.floor(y / PX_PER_MIN / 15) * 15;
    const start = new Date(col.day);
    start.setHours(START_HOUR, 0, 0, 0);
    start.setMinutes(start.getMinutes() + minutes);
    onCreate(start, col.professionalId);
  };

  return (
    <GridCard>
      <GridScroll>
        <GridHead style={{ gridTemplateColumns: `56px repeat(${columns.length}, minmax(140px, 1fr))` }}>
          <span />
          {columns.map((c) => (
            <ColumnTitle key={c.key} $today={isSameDay(c.day, new Date()) && !c.color}>
              {c.color && <ColorDot style={{ background: c.color }} aria-hidden />}
              {c.label}
            </ColumnTitle>
          ))}
        </GridHead>
        <GridBody style={{ gridTemplateColumns: `56px repeat(${columns.length}, minmax(140px, 1fr))` }}>
          <Hours style={{ height }}>
            {hours.map((h) => (
              <HourLabel key={h} style={{ top: (h - START_HOUR) * 60 * PX_PER_MIN }}>
                {String(h).padStart(2, "0")}:00
              </HourLabel>
            ))}
          </Hours>
          {columns.map((col) => {
            const events = appointments.filter(
              (a) =>
                isSameDay(new Date(a.startsAt), col.day) &&
                (!col.professionalId || a.professionalId === col.professionalId),
            );
            const lanes = layoutLanes(events);
            return (
              <DayColumn
                key={col.key}
                style={{ height }}
                onClick={(e) => clickSlot(e, col)}
                aria-label={`Coluna ${col.label}. Clique num horário vazio para agendar.`}
              >
                {hours.map((h) => (
                  <HourLine key={h} style={{ top: (h - START_HOUR) * 60 * PX_PER_MIN }} aria-hidden />
                ))}
                {blocks
                  .filter((b) => !col.professionalId || !b.professionalId || b.professionalId === col.professionalId)
                  .map((b) => {
                    const seg = blockSegment(b, col.day);
                    if (!seg) return null;
                    const label = `${b.reason}${!col.professionalId && b.professionalName ? ` · ${b.professionalName}` : ""}`;
                    return (
                      <BlockArea
                        key={b.id}
                        type="button"
                        disabled={!onBlock}
                        style={{
                          top: minutesFromStart(seg.start) * PX_PER_MIN,
                          height: ((seg.end.getTime() - seg.start.getTime()) / 60000) * PX_PER_MIN,
                        }}
                        onClick={() => onBlock?.(b)}
                        aria-label={`Bloqueado: ${label}${onBlock ? ". Clique para remover." : ""}`}
                        title={onBlock ? "Clique para remover o bloqueio" : undefined}
                      >
                        <CalendarOff size={13} aria-hidden /> {label}
                      </BlockArea>
                    );
                  })}
                {isSameDay(col.day, new Date()) && (
                  <NowLine style={{ top: minutesFromStart(new Date()) * PX_PER_MIN }} />
                )}
                {events.map((a) => {
                  const start = new Date(a.startsAt);
                  const end = new Date(a.endsAt);
                  const top = Math.max(0, minutesFromStart(start)) * PX_PER_MIN;
                  const h = Math.max(22, ((end.getTime() - start.getTime()) / 60000) * PX_PER_MIN - 2);
                  const lane = lanes.get(a.id) ?? { index: 0, total: 1 };
                  const status = APPOINTMENT_STATUS[a.status];
                  return (
                    <Event
                      key={a.id}
                      type="button"
                      style={{
                        top,
                        height: h,
                        left: `calc(${(lane.index / lane.total) * 100}% + 2px)`,
                        width: `calc(${100 / lane.total}% - 4px)`,
                        borderLeftColor: a.professionalColor,
                      }}
                      $muted={a.status === "NO_SHOW"}
                      onClick={() => onOpen(a.id)}
                      aria-label={`${formatTime(a.startsAt)} ${a.patientName}, ${a.professionalName}, ${status?.label}`}
                    >
                      <strong>
                        {formatTime(a.startsAt)} {a.patientName}
                      </strong>
                      {h > 36 && (
                        <span>
                          {a.procedureName ?? a.professionalName} · {status?.label}
                        </span>
                      )}
                    </Event>
                  );
                })}
              </DayColumn>
            );
          })}
        </GridBody>
      </GridScroll>
    </GridCard>
  );
}

function minutesFromStart(d: Date) {
  return (d.getHours() - START_HOUR) * 60 + d.getMinutes();
}

/** Distribui eventos sobrepostos lado a lado (faixas). */
function layoutLanes(events: Appointment[]) {
  const sorted = [...events].sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const result = new Map<string, { index: number; total: number }>();
  let cluster: Appointment[] = [];
  let laneEnds: number[] = [];
  let clusterEnd = 0;
  const flush = () => {
    const total = Math.max(1, laneEnds.length);
    cluster.forEach((a) => {
      const r = result.get(a.id);
      if (r) r.total = total;
    });
    cluster = [];
    laneEnds = [];
  };
  for (const a of sorted) {
    const s = new Date(a.startsAt).getTime();
    const e = new Date(a.endsAt).getTime();
    if (cluster.length > 0 && s >= clusterEnd) flush();
    let lane = laneEnds.findIndex((end) => end <= s);
    if (lane === -1) {
      lane = laneEnds.length;
      laneEnds.push(e);
    } else {
      laneEnds[lane] = e;
    }
    result.set(a.id, { index: lane, total: 1 });
    cluster.push(a);
    clusterEnd = Math.max(clusterEnd, e);
  }
  flush();
  return result;
}

/* ------------------------------------------------------------------ Mês */

function MonthView({
  date,
  appointments,
  onDay,
  onOpen,
}: {
  date: Date;
  appointments: Appointment[];
  onDay: (d: Date) => void;
  onOpen: (id: string) => void;
}) {
  const days = rangeFor("month", date).days;
  return (
    <GridCard>
      <MonthGrid>
        {["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"].map((d) => (
          <WeekdayName key={d}>{d}</WeekdayName>
        ))}
        {days.map((d) => {
          const events = appointments.filter((a) => isSameDay(new Date(a.startsAt), d));
          return (
            <MonthCell key={d.toISOString()} $outside={!isSameMonth(d, date)} $today={isSameDay(d, new Date())}>
              <DayNumber type="button" onClick={() => onDay(d)} aria-label={`Abrir ${format(d, "dd/MM")}`}>
                {format(d, "d")}
              </DayNumber>
              {events.slice(0, 3).map((a) => (
                <MonthEvent
                  key={a.id}
                  type="button"
                  onClick={() => onOpen(a.id)}
                  style={{ borderLeftColor: a.professionalColor }}
                >
                  {formatTime(a.startsAt)} {a.patientName.split(" ")[0]}
                </MonthEvent>
              ))}
              {events.length > 3 && (
                <More type="button" onClick={() => onDay(d)}>
                  +{events.length - 3} mais
                </More>
              )}
            </MonthCell>
          );
        })}
      </MonthGrid>
    </GridCard>
  );
}

/* ------------------------------------------------------------------ Lista */

function ListView({
  appointments,
  blocks,
  days,
  onOpen,
  onBlock,
}: {
  appointments: Appointment[];
  blocks: ScheduleBlock[];
  days: Date[];
  onOpen: (id: string) => void;
  onBlock?: (block: ScheduleBlock) => void;
}) {
  return (
    <Stack $gap={4}>
      {days.map((d) => {
        const events = appointments.filter((a) => isSameDay(new Date(a.startsAt), d));
        return (
          <Card key={d.toISOString()}>
            <ListDay>{format(d, "EEEE, dd/MM", { locale: ptBR })}</ListDay>
            {blocks
              .filter((b) => new Date(b.startsAt) < addDays(startOfDay(d), 1) && new Date(b.endsAt) > startOfDay(d))
              .map((b) => (
                <BlockLine key={b.id} type="button" disabled={!onBlock} onClick={() => onBlock?.(b)}>
                  <CalendarOff size={14} aria-hidden /> Bloqueado: {b.reason} ·{" "}
                  {b.professionalName ?? "clínica inteira"}
                </BlockLine>
              ))}
            {events.length === 0 ? (
              <Hint>Nenhum atendimento.</Hint>
            ) : (
              <ListItems>
                {events.map((a) => (
                  <li key={a.id}>
                    <ListButton
                      type="button"
                      onClick={() => onOpen(a.id)}
                      style={{ borderLeftColor: a.professionalColor }}
                    >
                      <time>
                        {formatTime(a.startsAt)}–{formatTime(a.endsAt)}
                      </time>
                      <span>
                        <strong>{a.patientName}</strong>
                        <small>
                          {a.professionalName}
                          {a.procedureName ? ` · ${a.procedureName}` : ""}
                        </small>
                      </span>
                      <Badge $tone={APPOINTMENT_STATUS[a.status]?.tone}>{APPOINTMENT_STATUS[a.status]?.label}</Badge>
                    </ListButton>
                  </li>
                ))}
              </ListItems>
            )}
          </Card>
        );
      })}
    </Stack>
  );
}

/* ------------------------------------------------------------------ Estilos */

const Toolbar = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-end;
  gap: ${({ theme }) => theme.space[4]};
`;

const Nav = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space[2]};
  flex-wrap: wrap;
`;

const PeriodLabel = styled.span`
  font-family: ${({ theme }) => theme.fonts.display};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  font-size: ${({ theme }) => theme.fontSizes.lg};
  text-transform: capitalize;
  margin-left: ${({ theme }) => theme.space[2]};
`;

const Filters = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: ${({ theme }) => theme.space[3]};

  @media (max-width: ${({ theme }) => theme.breakpoints.md - 1}px) {
    width: 100%;
  }
`;

const ProfessionalFilter = styled.div`
  min-width: 220px;

  @media (max-width: ${({ theme }) => theme.breakpoints.md - 1}px) {
    width: 100%;
  }
`;

const ViewSwitch = styled.div`
  display: inline-flex;
  background: ${({ theme }) => theme.colors.surface};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radii.pill};
  padding: 3px;
  height: 44px;

  /* No celular, a troca de visualização ocupa a largura do conteúdo, com as opções repartindo o espaço. */
  @media (max-width: ${({ theme }) => theme.breakpoints.md - 1}px) {
    display: flex;
    width: 100%;

    & > button {
      flex: 1 1 0;
      padding: 0 ${({ theme }) => theme.space[2]};
    }
  }
`;

const ViewButton = styled.button`
  border: none;
  background: transparent;
  border-radius: ${({ theme }) => theme.radii.pill};
  padding: 0 ${({ theme }) => theme.space[4]};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  color: ${({ theme }) => theme.colors.textMuted};
  cursor: pointer;

  &[aria-pressed="true"] {
    background: ${({ theme }) => theme.colors.primary};
    color: #fff;
  }
`;

const GridCard = styled(Card)`
  padding: 0;
  overflow: hidden;
`;

const GridScroll = styled.div`
  overflow-x: auto;
  max-height: 72vh;
  overflow-y: auto;
`;

const GridHead = styled.div`
  display: grid;
  position: sticky;
  top: 0;
  z-index: 2;
  background: linear-gradient(
    90deg,
    ${({ theme }) => theme.colors.tableHeaderStart},
    ${({ theme }) => theme.colors.tableHeaderEnd}
  );
  color: #fff;
`;

const ColumnTitle = styled.div<{ $today: boolean }>`
  padding: ${({ theme }) => theme.space[3]};
  font-size: ${({ theme }) => theme.fontSizes.sm};
  font-weight: ${({ theme }) => theme.fontWeights.bold};
  text-transform: capitalize;
  display: flex;
  align-items: center;
  gap: 6px;
  background: ${({ $today }) => ($today ? "rgba(255,255,255,0.15)" : "transparent")};
`;

const ColorDot = styled.span`
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 1px solid #fff;
`;

const GridBody = styled.div`
  display: grid;
  position: relative;
`;

const Hours = styled.div`
  position: relative;
  border-right: 1px solid ${({ theme }) => theme.colors.border};
`;

const HourLabel = styled.span`
  position: absolute;
  right: 6px;
  transform: translateY(-50%);
  font-family: ${({ theme }) => theme.fonts.mono};
  font-size: ${({ theme }) => theme.fontSizes.xs};
  color: ${({ theme }) => theme.colors.textMuted};
  &:first-child {
    transform: none;
  }
`;

const DayColumn = styled.div`
  position: relative;
  border-right: 1px solid ${({ theme }) => theme.colors.border};
  cursor: copy;
`;

const HourLine = styled.div`
  position: absolute;
  left: 0;
  right: 0;
  border-top: 1px solid ${({ theme }) => theme.colors.border};
  pointer-events: none;
`;

/** Período bloqueado na grade: faixa cinza com o motivo; atendimentos aparecem por cima. */
const BlockArea = styled.button`
  position: absolute;
  left: 0;
  right: 0;
  display: flex;
  align-items: flex-start;
  gap: 4px;
  padding: 4px 6px;
  border: 0;
  border-top: 1px dashed ${({ theme }) => theme.colors.borderStrong};
  background: ${({ theme }) => theme.colors.border};
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.xs};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  text-align: left;
  overflow: hidden;
  cursor: pointer;

  &:disabled {
    cursor: not-allowed;
  }
  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.borderStrong};
  }
`;

const BlockLine = styled.button`
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  margin: ${({ theme }) => theme.space[2]} 0;
  padding: ${({ theme }) => `${theme.space[2]} ${theme.space[3]}`};
  border: 0;
  border-radius: ${({ theme }) => theme.radii.md};
  background: ${({ theme }) => theme.colors.border};
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.sm};
  text-align: left;
  cursor: pointer;

  &:disabled {
    cursor: default;
  }
`;

const NowLine = styled.div`
  position: absolute;
  left: 0;
  right: 0;
  border-top: 2px solid ${({ theme }) => theme.colors.danger};
  pointer-events: none;
  z-index: 1;
`;

const Event = styled.button<{ $muted: boolean }>`
  position: absolute;
  display: grid;
  align-content: start;
  gap: 1px;
  text-align: left;
  overflow: hidden;
  padding: 3px 6px;
  border: none;
  border-left: 4px solid;
  border-radius: 6px;
  background: ${({ theme, $muted }) => ($muted ? theme.colors.dangerSoft : theme.colors.primarySoft)};
  color: ${({ theme }) => theme.colors.text};
  font-size: ${({ theme }) => theme.fontSizes.xs};
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
  z-index: 1;

  strong,
  span {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  span {
    color: ${({ theme }) => theme.colors.textMuted};
  }
  &:hover {
    filter: brightness(0.97);
  }
  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
`;

const MonthGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(7, minmax(90px, 1fr));
  overflow-x: auto;
`;

const WeekdayName = styled.div`
  padding: ${({ theme }) => theme.space[2]};
  background: ${({ theme }) => theme.colors.tableHeaderEnd};
  color: #fff;
  font-size: ${({ theme }) => theme.fontSizes.xs};
  font-weight: ${({ theme }) => theme.fontWeights.bold};
  text-transform: uppercase;
`;

const MonthCell = styled.div<{ $outside: boolean; $today: boolean }>`
  min-height: 108px;
  padding: ${({ theme }) => theme.space[1]};
  border-right: 1px solid ${({ theme }) => theme.colors.border};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme, $outside, $today }) =>
    $today ? theme.colors.primarySoft : $outside ? theme.colors.surfaceMuted : theme.colors.surface};
  display: grid;
  align-content: start;
  gap: 2px;
  opacity: ${({ $outside }) => ($outside ? 0.7 : 1)};
`;

const DayNumber = styled.button`
  justify-self: start;
  border: none;
  background: transparent;
  font-weight: ${({ theme }) => theme.fontWeights.bold};
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 6px;
  &:hover {
    background: ${({ theme }) => theme.colors.primarySoft};
  }
`;

const MonthEvent = styled.button`
  text-align: left;
  border: none;
  border-left: 3px solid;
  background: ${({ theme }) => theme.colors.surfaceMuted};
  border-radius: 4px;
  padding: 1px 4px;
  font-size: ${({ theme }) => theme.fontSizes.xs};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
`;

const More = styled.button`
  border: none;
  background: transparent;
  text-align: left;
  font-size: ${({ theme }) => theme.fontSizes.xs};
  color: ${({ theme }) => theme.colors.primaryText};
  cursor: pointer;
`;

const ListDay = styled.h2`
  font-size: ${({ theme }) => theme.fontSizes.base};
  text-transform: capitalize;
  margin-bottom: ${({ theme }) => theme.space[3]};
`;

const ListItems = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: ${({ theme }) => theme.space[2]};
`;

const ListButton = styled.button`
  width: 100%;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: ${({ theme }) => theme.space[3]};
  text-align: left;
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-left: 4px solid;
  border-radius: ${({ theme }) => theme.radii.md};
  background: ${({ theme }) => theme.colors.surface};
  padding: ${({ theme }) => theme.space[3]};
  cursor: pointer;

  time {
    font-family: ${({ theme }) => theme.fonts.mono};
    font-size: ${({ theme }) => theme.fontSizes.sm};
  }
  span {
    display: grid;
    min-width: 0;
  }
  small {
    color: ${({ theme }) => theme.colors.textMuted};
  }
`;

const Hint = styled.p`
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.sm};
`;
