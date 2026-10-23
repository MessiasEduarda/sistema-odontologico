"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { useCallback, useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
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
  variant?: "primary" | "sub";
}

export function Tabs({ items, active, onChange, label, variant = "primary" }: TabsProps) {
  const base = useId();
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const listRef = useRef<HTMLDivElement | null>(null);
  const [edges, setEdges] = useState({ left: false, right: false });

  const updateEdges = useCallback(() => {
    const el = listRef.current;
    if (!el) return;
    const left = el.scrollLeft > 2;
    const right = el.scrollLeft + el.clientWidth < el.scrollWidth - 2;
    setEdges((e) => (e.left === left && e.right === right ? e : { left, right }));
  }, []);

  useEffect(() => {
    const el = listRef.current;
    if (!el || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(updateEdges);
    observer.observe(el);
    Array.from(el.children).forEach((child) => observer.observe(child));
    return () => observer.disconnect();
  }, [updateEdges, items.length]);

  const scrollBy = (direction: 1 | -1) => {
    const el = listRef.current;
    el?.scrollBy({ left: direction * el.clientWidth * 0.7, behavior: "smooth" });
  };

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
  const currentIndex = items.findIndex((i) => i.id === current?.id);

  useEffect(() => {
    refs.current[currentIndex]?.scrollIntoView({ block: "nearest", inline: "center" });
  }, [currentIndex]);

  return (
    <div>
      <Bar>
        {edges.left && (
          <ScrollButton type="button" tabIndex={-1} aria-hidden="true" $side="left" onClick={() => scrollBy(-1)}>
            <ChevronLeft size={18} />
          </ScrollButton>
        )}
        <List ref={listRef} role="tablist" aria-label={label} onScroll={updateEdges} $variant={variant}>
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
                $variant={variant}
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
        {edges.right && (
          <ScrollButton type="button" tabIndex={-1} aria-hidden="true" $side="right" onClick={() => scrollBy(1)}>
            <ChevronRight size={18} />
          </ScrollButton>
        )}
      </Bar>
      {current && (
        <Panel role="tabpanel" id={`${base}-panel-${current.id}`} aria-labelledby={`${base}-tab-${current.id}`}>
          {current.content}
        </Panel>
      )}
    </div>
  );
}

const Bar = styled.div`
  position: relative;
`;

const ScrollButton = styled.button<{ $side: "left" | "right" }>`
  position: absolute;
  top: 0;
  bottom: 1px;
  ${({ $side }) => $side}: 0;
  z-index: 1;
  width: 40px;
  display: grid;
  place-items: center;
  border: 0;
  padding: 0;
  color: ${({ theme }) => theme.colors.primaryText};
  background: linear-gradient(
    to ${({ $side }) => ($side === "left" ? "right" : "left")},
    ${({ theme }) => theme.colors.background} 55%,
    transparent
  );
  cursor: pointer;
`;

const List = styled.div<{ $variant: "primary" | "sub" }>`
  display: flex;
  gap: ${({ theme }) => theme.space[1]};
  overflow-x: auto;
  overflow-y: hidden;
  box-shadow: ${({ theme, $variant }) => ($variant === "primary" ? `inset 0 -1px 0 ${theme.colors.border}` : "none")};
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
`;

const Tab = styled.button<{ $variant: "primary" | "sub" }>`
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

  ${({ theme, $variant }) =>
    $variant === "sub" &&
    `
    border-bottom: 0;
    border-radius: ${theme.radii.pill};
    padding: ${theme.space[2]} ${theme.space[4]};
    font-size: ${theme.fontSizes.sm};

    &[aria-selected="true"] {
      background: ${theme.colors.primarySoft};
      color: ${theme.colors.primaryText};
    }
    &:hover:not([aria-selected="true"]) {
      background: ${theme.colors.surface};
    }
    &:focus-visible {
      border-radius: ${theme.radii.pill};
    }
  `}
`;

const Panel = styled.div`
  padding-top: ${({ theme }) => theme.space[5]};
`;
