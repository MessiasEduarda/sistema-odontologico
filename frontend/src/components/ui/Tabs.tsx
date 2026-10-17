"use client";

import { useId, useRef, type KeyboardEvent, type ReactNode } from "react";
import styled from "styled-components";

export interface TabItem {
  id: string;
  label: string;
  content: ReactNode;
}

interface TabsProps {
  items: TabItem[];
  active: string;
  onChange: (id: string) => void;
  label: string;
}

/** Abas acessíveis (padrão WAI-ARIA): setas esquerda/direita, Home e End. */
export function Tabs({ items, active, onChange, label }: TabsProps) {
  const base = useId();
  const refs = useRef<(HTMLButtonElement | null)[]>([]);

  const onKey = (e: KeyboardEvent, index: number) => {
    let next = index;
    if (e.key === "ArrowRight") next = (index + 1) % items.length;
    else if (e.key === "ArrowLeft") next = (index - 1 + items.length) % items.length;
    else if (e.key === "Home") next = 0;
    else if (e.key === "End") next = items.length - 1;
    else return;
    e.preventDefault();
    const target = items[next];
    if (target) {
      onChange(target.id);
      refs.current[next]?.focus();
    }
  };

  const current = items.find((i) => i.id === active) ?? items[0];

  return (
    <div>
      <List role="tablist" aria-label={label}>
        {items.map((item, index) => {
          const selected = item.id === current?.id;
          return (
            <Tab
              key={item.id}
              ref={(el) => {
                refs.current[index] = el;
              }}
              role="tab"
              type="button"
              id={`${base}-tab-${item.id}`}
              aria-selected={selected}
              aria-controls={`${base}-panel-${item.id}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(item.id)}
              onKeyDown={(e) => onKey(e, index)}
            >
              {item.label}
            </Tab>
          );
        })}
      </List>
      {current && (
        <Panel role="tabpanel" id={`${base}-panel-${current.id}`} aria-labelledby={`${base}-tab-${current.id}`}>
          {current.content}
        </Panel>
      )}
    </div>
  );
}

/** Em telas estreitas as abas deslizam para o lado, sem barra de rolagem visível. */
const List = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.space[1]};
  overflow-x: auto;
  overflow-y: hidden;
  box-shadow: inset 0 -1px 0 ${({ theme }) => theme.colors.border};
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
`;

const Tab = styled.button`
  border: none;
  background: transparent;
  padding: ${({ theme }) => `${theme.space[3]} ${theme.space[4]}`};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  color: ${({ theme }) => theme.colors.textMuted};
  border-bottom: 2px solid transparent;
  white-space: nowrap;
  cursor: pointer;

  &[aria-selected="true"] {
    color: ${({ theme }) => theme.colors.primaryText};
    border-bottom-color: ${({ theme }) => theme.colors.primary};
  }
  &:hover {
    color: ${({ theme }) => theme.colors.text};
  }
  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
    border-radius: ${({ theme }) => theme.radii.sm};
  }
`;

const Panel = styled.div`
  padding-top: ${({ theme }) => theme.space[5]};
`;
