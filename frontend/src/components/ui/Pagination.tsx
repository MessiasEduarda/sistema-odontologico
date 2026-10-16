"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import styled from "styled-components";

export interface PaginationProps {
  /** Página atual, começando em 0. */
  page: number;
  totalPages: number;
  totalItems: number;
  /** Quantos itens aparecem nesta página (para "Mostrando X de Y"). */
  shownItems: number;
  onChange: (page: number) => void;
}

type Slot = number | "gap";

/**
 * Números visíveis: sempre a primeira e a última página, a atual e suas vizinhas; o resto vira "…".
 * Ex.: [1] 2 3 … 10 · 1 … 4 [5] 6 … 10 · 1 … 8 9 [10].
 */
export function pageSlots(page: number, totalPages: number): Slot[] {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i);
  const near = new Set([0, totalPages - 1, page - 1, page, page + 1]);
  if (page <= 2) [1, 2, 3].forEach((p) => near.add(p));
  if (page >= totalPages - 3) [totalPages - 4, totalPages - 3, totalPages - 2].forEach((p) => near.add(p));
  const pages = [...near].filter((p) => p >= 0 && p < totalPages).sort((a, b) => a - b);
  const slots: Slot[] = [];
  let previous = -1;
  for (const p of pages) {
    if (previous >= 0 && p - previous > 1) slots.push("gap");
    slots.push(p);
    previous = p;
  }
  return slots;
}

/** Paginador padrão das tabelas: "Mostrando X de Y", setas e números (página atual em círculo caramelo). */
export function Pagination({ page, totalPages, totalItems, shownItems, onChange }: PaginationProps) {
  if (totalItems === 0) return null;
  const last = Math.max(totalPages - 1, 0);
  return (
    <Pager aria-label="Paginação">
      <Summary>
        Mostrando {shownItems} de {totalItems}
      </Summary>
      <Arrow type="button" aria-label="Página anterior" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        <ChevronLeft size={18} aria-hidden />
      </Arrow>
      <Pages>
        {pageSlots(page, totalPages).map((slot, i) =>
          slot === "gap" ? (
            <Gap key={`gap-${i}`} aria-hidden>
              …
            </Gap>
          ) : (
            <PageButton
              key={slot}
              type="button"
              aria-label={`Página ${slot + 1}`}
              aria-current={slot === page ? "page" : undefined}
              onClick={() => slot !== page && onChange(slot)}
            >
              {slot + 1}
            </PageButton>
          ),
        )}
      </Pages>
      <Arrow type="button" aria-label="Próxima página" disabled={page >= last} onClick={() => onChange(page + 1)}>
        <ChevronRight size={18} aria-hidden />
      </Arrow>
    </Pager>
  );
}

const Pager = styled.nav`
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[1]};
  margin-top: ${({ theme }) => theme.space[3]};
`;

const Summary = styled.span`
  margin-right: ${({ theme }) => theme.space[3]};
  font-size: ${({ theme }) => theme.fontSizes.md};
  color: ${({ theme }) => theme.colors.textMuted};
`;

const Pages = styled.div`
  display: flex;
  align-items: center;
  gap: 2px;
`;

const Arrow = styled.button`
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: ${({ theme }) => theme.colors.primaryText};
  cursor: pointer;

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.primarySoft};
  }
  &:disabled {
    color: ${({ theme }) => theme.colors.borderStrong};
    cursor: default;
  }
  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
`;

const PageButton = styled.button`
  min-width: 32px;
  height: 32px;
  padding: 0 6px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.md};
  font-weight: ${({ theme }) => theme.fontWeights.medium};
  cursor: pointer;

  &:hover:not([aria-current="page"]) {
    background: ${({ theme }) => theme.colors.primarySoft};
    color: ${({ theme }) => theme.colors.primaryText};
  }
  &[aria-current="page"] {
    background: ${({ theme }) => theme.colors.primary};
    color: ${({ theme }) => theme.colors.onPrimary};
    font-weight: ${({ theme }) => theme.fontWeights.semibold};
    cursor: default;
  }
  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
`;

const Gap = styled.span`
  min-width: 24px;
  text-align: center;
  color: ${({ theme }) => theme.colors.textSubtle};
`;
