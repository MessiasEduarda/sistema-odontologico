"use client";

import { type RefObject, useEffect, useLayoutEffect } from "react";
import styled from "styled-components";

export function useAnchoredList(
  open: boolean,
  anchorRef: RefObject<HTMLElement | null>,
  listRef: RefObject<HTMLElement | null>,
) {
  useLayoutEffect(() => {
    const list = listRef.current;
    const anchor = anchorRef.current;
    if (!open || !list || !anchor) return;

    const place = () => {
      const rect = anchor.getBoundingClientRect();
      const gap = 6;
      const below = window.innerHeight - rect.bottom - gap - 8;
      const above = rect.top - gap - 8;
      const wanted = Math.min(list.scrollHeight, 300);
      const up = below < Math.min(wanted, 200) && above > below;
      const maxHeight = Math.max(120, Math.min(300, up ? above : below));
      list.style.left = `${Math.max(8, rect.left)}px`;
      list.style.width = `${rect.width}px`;
      list.style.maxHeight = `${maxHeight}px`;
      if (up) {
        list.style.top = "auto";
        list.style.bottom = `${window.innerHeight - rect.top + gap}px`;
      } else {
        list.style.bottom = "auto";
        list.style.top = `${rect.bottom + gap}px`;
      }
    };

    const supportsPopover = typeof list.showPopover === "function";
    if (supportsPopover && !list.matches(":popover-open")) list.showPopover();
    place();
    window.addEventListener("resize", place);
    window.addEventListener("scroll", place, true);
    return () => {
      window.removeEventListener("resize", place);
      window.removeEventListener("scroll", place, true);
      if (supportsPopover && list.matches(":popover-open")) list.hidePopover();
    };
  }, [open, anchorRef, listRef]);
}

export function useClickOutside(open: boolean, refs: RefObject<HTMLElement | null>[], onOutside: () => void) {
  useEffect(() => {
    if (!open) return;
    const handler = (e: PointerEvent) => {
      const target = e.target as Node;
      if (refs.every((r) => !r.current?.contains(target))) onOutside();
    };
    document.addEventListener("pointerdown", handler, true);
    return () => document.removeEventListener("pointerdown", handler, true);
  }, [open, refs, onOutside]);
}

export const ListPanel = styled.ul`
  position: fixed;
  inset: auto;
  z-index: ${({ theme }) => theme.zIndex.dropdown};
  margin: 0;
  padding: ${({ theme }) => theme.space[1]};
  list-style: none;
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.text};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radii.lg};
  box-shadow: ${({ theme }) => theme.shadows.overlay};
  overflow-y: auto;
  overscroll-behavior: contain;
  font-family: ${({ theme }) => theme.fonts.body};
  font-size: ${({ theme }) => theme.fontSizes.md};

  &:popover-open {
    display: block;
  }
`;

export const ListOption = styled.li<{ $active?: boolean }>`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${({ theme }) => theme.space[2]};
  min-height: 38px;
  padding: ${({ theme }) => `${theme.space[2]} ${theme.space[3]}`};
  border-radius: ${({ theme }) => theme.radii.md};
  cursor: pointer;
  user-select: none;
  background: ${({ theme, $active }) => ($active ? theme.colors.primarySoft : "transparent")};
  color: ${({ theme }) => theme.colors.text};

  &[aria-selected="true"] {
    color: ${({ theme }) => theme.colors.primaryText};
    font-weight: ${({ theme }) => theme.fontWeights.semibold};
  }
  &[aria-disabled="true"] {
    color: ${({ theme }) => theme.colors.textSubtle};
    cursor: default;
  }
  small {
    color: ${({ theme }) => theme.colors.textMuted};
    font-weight: ${({ theme }) => theme.fontWeights.regular};
  }
  svg {
    flex-shrink: 0;
    color: ${({ theme }) => theme.colors.primary};
  }
`;

export const ListEmpty = styled.li`
  padding: ${({ theme }) => theme.space[3]};
  color: ${({ theme }) => theme.colors.textMuted};
`;

export function popoverMode(): "manual" | undefined {
  return typeof HTMLElement !== "undefined" && "showPopover" in HTMLElement.prototype ? "manual" : undefined;
}
