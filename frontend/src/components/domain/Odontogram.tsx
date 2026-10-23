"use client";

import { useMemo } from "react";
import styled from "styled-components";
import type { ChartEntry } from "@/lib/api/types";
import { conditionInfo, TOOTH_CONDITIONS } from "@/lib/labels";

const PERMANENT_UPPER = [18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28];
const PERMANENT_LOWER = [48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38];
const DECIDUOUS_UPPER = [55, 54, 53, 52, 51, 61, 62, 63, 64, 65];
const DECIDUOUS_LOWER = [85, 84, 83, 82, 81, 71, 72, 73, 74, 75];

const WHOLE_TOOTH = new Set(["MISSING", "EXTRACTION", "IMPLANT", "CROWN", "ROOT_CANAL", "PROSTHESIS", "MOBILITY"]);

interface OdontogramProps {
  entries: ChartEntry[];
  dentition: "permanent" | "deciduous";
  onSelectTooth?: (tooth: number) => void;
  selectedTooth?: number | null;
}

type ToothState = { surfaces: Record<string, ChartEntry>; whole: ChartEntry | null; count: number };

export function Odontogram({ entries, dentition, onSelectTooth, selectedTooth }: OdontogramProps) {
  const state = useMemo(() => {
    const map = new Map<number, ToothState>();
    for (const e of entries) {
      if (e.voidedAt) continue;
      const s = map.get(e.tooth) ?? { surfaces: {}, whole: null, count: 0 };
      s.count += 1;
      if (WHOLE_TOOTH.has(e.condition) || !e.surfaces) s.whole = e;
      else for (const f of e.surfaces.split("")) s.surfaces[f === "P" ? "L" : f === "I" ? "O" : f] = e;
      map.set(e.tooth, s);
    }
    return map;
  }, [entries]);

  const upper = dentition === "permanent" ? PERMANENT_UPPER : DECIDUOUS_UPPER;
  const lower = dentition === "permanent" ? PERMANENT_LOWER : DECIDUOUS_LOWER;

  const renderTooth = (tooth: number) => {
    const s = state.get(tooth);
    const label = describe(tooth, s);
    return (
      <ToothButton
        key={tooth}
        type="button"
        aria-label={label}
        title={label}
        aria-pressed={selectedTooth === tooth}
        onClick={() => onSelectTooth?.(tooth)}
      >
        <span>{tooth}</span>
        <ToothSvg tooth={tooth} state={s} />
      </ToothButton>
    );
  };

  return (
    <Wrapper>
      <Arch aria-label="Arcada superior">
        <Half>{upper.slice(0, upper.length / 2).map(renderTooth)}</Half>
        <Midline aria-hidden="true" />
        <Half>{upper.slice(upper.length / 2).map(renderTooth)}</Half>
      </Arch>
      <Arch aria-label="Arcada inferior">
        <Half>{lower.slice(0, lower.length / 2).map(renderTooth)}</Half>
        <Midline aria-hidden="true" />
        <Half>{lower.slice(lower.length / 2).map(renderTooth)}</Half>
      </Arch>
      <Legend aria-label="Legenda">
        {TOOTH_CONDITIONS.map((c) => (
          <li key={c.value}>
            <Swatch style={{ background: c.color }} aria-hidden="true" />
            {c.label}
          </li>
        ))}
        <li>
          <Swatch style={{ background: "#fff", border: "2px dashed #555" }} aria-hidden="true" />
          Planejado
        </li>
      </Legend>
    </Wrapper>
  );
}

function describe(tooth: number, s: ToothState | undefined) {
  if (!s) return `Dente ${tooth}: sem registros`;
  const parts: string[] = [];
  if (s.whole) parts.push(conditionInfo(s.whole.condition).label);
  for (const [face, e] of Object.entries(s.surfaces)) parts.push(`${conditionInfo(e.condition).label} (${face})`);
  return `Dente ${tooth}: ${parts.join(", ")}`;
}

function ToothSvg({ tooth, state }: { tooth: number; state: ToothState | undefined }) {
  const quadrant = Math.floor(tooth / 10);
  const mesialOnRight = [1, 4, 5, 8].includes(quadrant);
  const fill = (face: string) => {
    const e = state?.surfaces[face];
    if (!e) return { fill: "#fff", stroke: "#B9B1A8", dash: undefined as string | undefined };
    const color = conditionInfo(e.condition).color;
    return e.status === "PLANNED"
      ? { fill: "#fff", stroke: color, dash: "3 2" }
      : { fill: color, stroke: "#6B655F", dash: undefined };
  };
  const left = fill(mesialOnRight ? "D" : "M");
  const right = fill(mesialOnRight ? "M" : "D");
  const top = fill("V");
  const bottom = fill("L");
  const center = fill("O");
  const whole = state?.whole;
  const wholeColor = whole ? conditionInfo(whole.condition).color : null;

  return (
    <svg viewBox="0 0 40 40" width="34" height="34" aria-hidden="true">
      <polygon points="0,0 40,0 28,12 12,12" fill={top.fill} stroke={top.stroke} strokeDasharray={top.dash} />
      <polygon
        points="0,40 40,40 28,28 12,28"
        fill={bottom.fill}
        stroke={bottom.stroke}
        strokeDasharray={bottom.dash}
      />
      <polygon points="0,0 12,12 12,28 0,40" fill={left.fill} stroke={left.stroke} strokeDasharray={left.dash} />
      <polygon points="40,0 28,12 28,28 40,40" fill={right.fill} stroke={right.stroke} strokeDasharray={right.dash} />
      <rect
        x="12"
        y="12"
        width="16"
        height="16"
        fill={center.fill}
        stroke={center.stroke}
        strokeDasharray={center.dash}
      />
      {whole && whole.condition === "MISSING" && (
        <g stroke="#1E1C1A" strokeWidth="3">
          <line x1="4" y1="4" x2="36" y2="36" />
          <line x1="36" y1="4" x2="4" y2="36" />
        </g>
      )}
      {whole && whole.condition !== "MISSING" && (
        <circle
          cx="20"
          cy="20"
          r="17"
          fill="none"
          stroke={wholeColor ?? "#000"}
          strokeWidth="3"
          strokeDasharray={whole.status === "PLANNED" ? "4 3" : undefined}
        />
      )}
    </svg>
  );
}

const Wrapper = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[4]};
  overflow-x: auto;
  padding-bottom: ${({ theme }) => theme.space[2]};
`;

const Arch = styled.div`
  display: flex;
  justify-content: center;
  align-items: stretch;
  gap: ${({ theme }) => theme.space[2]};
  min-width: max-content;
`;

const Half = styled.div`
  display: flex;
  gap: 4px;
`;

const Midline = styled.div`
  width: 2px;
  background: ${({ theme }) => theme.colors.borderStrong};
`;

const ToothButton = styled.button`
  display: grid;
  justify-items: center;
  gap: 2px;
  padding: 4px 2px;
  border: 1px solid transparent;
  border-radius: ${({ theme }) => theme.radii.sm};
  background: transparent;
  cursor: pointer;
  font-size: ${({ theme }) => theme.fontSizes.xs};
  font-family: ${({ theme }) => theme.fonts.mono};
  color: ${({ theme }) => theme.colors.textMuted};

  &:hover {
    background: ${({ theme }) => theme.colors.surfaceMuted};
  }
  &[aria-pressed="true"] {
    border-color: ${({ theme }) => theme.colors.primary};
    background: ${({ theme }) => theme.colors.primarySoft};
  }
  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
`;

const Legend = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => `${theme.space[2]} ${theme.space[4]}`};
  font-size: ${({ theme }) => theme.fontSizes.sm};
  color: ${({ theme }) => theme.colors.textMuted};

  li {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
`;

const Swatch = styled.span`
  width: 14px;
  height: 14px;
  border-radius: 3px;
  display: inline-block;
`;
