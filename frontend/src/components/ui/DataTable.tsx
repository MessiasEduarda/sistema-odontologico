"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import type { ReactNode } from "react";
import styled from "styled-components";
import { EmptyState, ErrorState } from "./display";
import { IconButton } from "./IconButton";

export interface Column<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  /** Largura mínima (evita quebra de cabeçalhos e códigos). */
  minWidth?: string;
  align?: "left" | "right" | "center";
  /** Oculta a coluna na visão em cartões do celular. */
  hideOnMobile?: boolean;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[] | undefined;
  rowKey: (row: T) => string;
  caption: string;
  loading?: boolean;
  error?: Error | null;
  onRetry?: () => void;
  emptyTitle?: string;
  emptyDescription?: ReactNode;
  emptyAction?: ReactNode;
  onRowClick?: (row: T) => void;
}

/**
 * Tabela padrão: cabeçalho caramelo em gradiente (referências), linhas espaçadas; no celular vira lista de cartões.
 * Inclui estados de carregamento, erro e vazio.
 */
export function DataTable<T>({
  columns,
  rows,
  rowKey,
  caption,
  loading,
  error,
  onRetry,
  emptyTitle = "Nada por aqui ainda",
  emptyDescription,
  emptyAction,
  onRowClick,
}: DataTableProps<T>) {
  if (error) return <ErrorState error={error} onRetry={onRetry} />;

  return (
    <Wrapper aria-busy={loading || undefined}>
      <Table>
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr>
            {columns.map((c) => (
              <Th key={c.key} scope="col" style={{ minWidth: c.minWidth, textAlign: c.align ?? "left" }}>
                {c.header}
              </Th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading &&
            Array.from({ length: 4 }, (_, i) => (
              <tr key={`skeleton-${i}`}>
                {columns.map((c) => (
                  <Td key={c.key} data-label={c.header}>
                    <Skeleton />
                  </Td>
                ))}
              </tr>
            ))}
          {!loading &&
            rows?.map((row) => (
              <Tr
                key={rowKey(row)}
                $clickable={Boolean(onRowClick)}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
              >
                {columns.map((c) => (
                  <Td
                    key={c.key}
                    data-label={c.header}
                    $hideOnMobile={c.hideOnMobile}
                    style={{ textAlign: c.align ?? "left" }}
                  >
                    {c.render(row)}
                  </Td>
                ))}
              </Tr>
            ))}
        </tbody>
      </Table>
      {!loading && rows?.length === 0 && (
        <EmptyState title={emptyTitle} description={emptyDescription} action={emptyAction} />
      )}
    </Wrapper>
  );
}

interface PaginationProps {
  page: number;
  totalPages: number;
  totalItems: number;
  onChange: (page: number) => void;
}

export function Pagination({ page, totalPages, totalItems, onChange }: PaginationProps) {
  if (totalPages <= 1) return null;
  return (
    <Pager aria-label="Paginação">
      <span>
        Página {page + 1} de {totalPages} · {totalItems} registro(s)
      </span>
      <IconButton icon={ChevronLeft} label="Página anterior" disabled={page === 0} onClick={() => onChange(page - 1)} />
      <IconButton
        icon={ChevronRight}
        label="Próxima página"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      />
    </Pager>
  );
}

const Wrapper = styled.div`
  background: ${({ theme }) => theme.colors.surface};
  border-radius: ${({ theme }) => theme.radii.lg};
  box-shadow: ${({ theme }) => theme.shadows.card};
  overflow: hidden;

  @media (min-width: ${({ theme }) => theme.breakpoints.md}px) {
    overflow-x: auto;
  }
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;

  @media (max-width: ${({ theme }) => theme.breakpoints.md - 1}px) {
    thead {
      display: none;
    }
    tbody,
    tr,
    td {
      display: block;
    }
    tr {
      padding: ${({ theme }) => theme.space[4]};
      border-bottom: 1px solid ${({ theme }) => theme.colors.border};
    }
    td {
      display: flex;
      justify-content: space-between;
      gap: ${({ theme }) => theme.space[3]};
      padding: ${({ theme }) => theme.space[1]} 0;
      border: none;
      text-align: right !important;
    }
    td::before {
      content: attr(data-label);
      color: ${({ theme }) => theme.colors.textMuted};
      font-size: ${({ theme }) => theme.fontSizes.sm};
      text-align: left;
    }
  }
`;

const Th = styled.th`
  background: linear-gradient(
    90deg,
    ${({ theme }) => theme.colors.tableHeaderStart},
    ${({ theme }) => theme.colors.tableHeaderEnd}
  );
  color: #fff;
  font-size: ${({ theme }) => theme.fontSizes.xs};
  font-weight: ${({ theme }) => theme.fontWeights.bold};
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: ${({ theme }) => `${theme.space[4]} ${theme.space[5]}`};
  white-space: nowrap;
`;

const Tr = styled.tr<{ $clickable: boolean }>`
  cursor: ${({ $clickable }) => ($clickable ? "pointer" : "default")};
  transition: background ${({ theme }) => theme.transitions.fast};

  &:hover {
    background: ${({ theme, $clickable }) => ($clickable ? theme.colors.surfaceMuted : "transparent")};
  }
`;

const Td = styled.td<{ $hideOnMobile?: boolean }>`
  padding: ${({ theme }) => `${theme.space[4]} ${theme.space[5]}`};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  vertical-align: middle;

  @media (max-width: ${({ theme }) => theme.breakpoints.md - 1}px) {
    display: ${({ $hideOnMobile }) => ($hideOnMobile ? "none !important" : "flex")};
  }
`;

const Skeleton = styled.span`
  display: block;
  height: 12px;
  width: 70%;
  border-radius: 6px;
  background: linear-gradient(90deg, #eeeae6, #f7f5f2, #eeeae6);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite linear;

  @keyframes shimmer {
    from {
      background-position: 200% 0;
    }
    to {
      background-position: -200% 0;
    }
  }
`;

const Pager = styled.nav`
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: ${({ theme }) => theme.space[2]};
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.sm};

  span {
    margin-right: ${({ theme }) => theme.space[2]};
  }
`;

/** Célula com ações (botões de ícone) alinhadas. */
export const RowActions = styled.div`
  display: inline-flex;
  gap: ${({ theme }) => theme.space[2]};
`;
