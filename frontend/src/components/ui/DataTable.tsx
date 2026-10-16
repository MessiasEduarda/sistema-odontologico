"use client";

import { useCallback, useRef, useState, type ReactNode } from "react";
import styled from "styled-components";
import { EmptyState, ErrorState } from "./display";
import { Pagination } from "./Pagination";

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
  /** Itens por página na paginação automática (padrão 10). */
  pageSize?: number;
  /** Lista já paginada pelo servidor: a tabela só mostra o paginador com estes dados. */
  serverPagination?: { page: number; totalPages: number; totalItems: number; onChange: (page: number) => void };
}

/** Componentes que já usavam o paginador antigo continuam importando daqui. */
export { Pagination };

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
  pageSize = 10,
  serverPagination,
}: DataTableProps<T>) {
  const [page, setPage] = useState(0);
  // Altura real de uma linha de dados desta tabela (varia com etiquetas e botões), medida no navegador.
  const [rowHeight, setRowHeight] = useState(0);
  const observer = useRef<ResizeObserver | null>(null);
  const measureRow = useCallback((row: HTMLTableRowElement | null) => {
    observer.current?.disconnect();
    if (!row || typeof ResizeObserver === "undefined") return;
    observer.current = new ResizeObserver(() => setRowHeight(Math.round(row.getBoundingClientRect().height)));
    observer.current.observe(row);
  }, []);
  const total = rows?.length ?? 0;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  if (error) return <ErrorState error={error} onRetry={onRetry} />;

  // Se a lista diminuir (busca, filtro, exclusão), fica na última página que ainda existe.
  const current = Math.min(page, totalPages - 1);
  const visible = serverPagination ? rows : rows?.slice(current * pageSize, current * pageSize + pageSize);

  return (
    <div>
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
              visible?.map((row, index) => (
                <Tr
                  key={rowKey(row)}
                  ref={index === 0 ? measureRow : undefined}
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
            {/* Espaço em branco (sem linhas) para a tabela ter sempre a altura de uma página cheia de dados. */}
            {!loading && rowHeight > 0 && (visible?.length ?? 0) > 0 && (visible?.length ?? 0) < pageSize && (
              <FillerRow aria-hidden="true">
                <td colSpan={columns.length} style={{ height: (pageSize - (visible?.length ?? 0)) * rowHeight }} />
              </FillerRow>
            )}
          </tbody>
        </Table>
        {!loading && rows?.length === 0 && (
          <EmptyState title={emptyTitle} description={emptyDescription} action={emptyAction} />
        )}
      </Wrapper>
      {!loading &&
        (serverPagination ? (
          <Pagination {...serverPagination} shownItems={rows?.length ?? 0} />
        ) : (
          <Pagination
            page={current}
            totalPages={totalPages}
            totalItems={total}
            shownItems={visible?.length ?? 0}
            onChange={setPage}
          />
        ))}
    </div>
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

/** Espaço de preenchimento (só na visão de tabela; no celular, em cartões, não aparece). */
const FillerRow = styled.tr`
  @media (max-width: ${({ theme }) => theme.breakpoints.md - 1}px) {
    display: none !important;
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

/** Célula com ações (botões de ícone) alinhadas. */
export const RowActions = styled.div`
  display: inline-flex;
  gap: ${({ theme }) => theme.space[2]};
`;
