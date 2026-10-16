"use client";

import type { LucideIcon } from "lucide-react";
import { forwardRef, type ButtonHTMLAttributes } from "react";
import styled from "styled-components";

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon: LucideIcon;
  /** Obrigatório: nome acessível e dica (title) do botão. */
  label: string;
  tone?: "default" | "danger" | "inverse";
}

/** Botão só com ícone (ações de tabela, fechar modal). Área de toque de 40px e rótulo acessível obrigatório. */
export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  { icon: Icon, label, tone = "default", type = "button", ...rest },
  ref,
) {
  return (
    <Btn ref={ref} type={type} aria-label={label} title={label} $tone={tone} {...rest}>
      <Icon size={18} strokeWidth={1.8} aria-hidden="true" />
    </Btn>
  );
});

const Btn = styled.button<{ $tone: string }>`
  width: 40px;
  height: 40px;
  display: inline-grid;
  place-items: center;
  border-radius: ${({ theme }) => theme.radii.md};
  border: 1px solid ${({ theme, $tone }) => ($tone === "inverse" ? "transparent" : theme.colors.border)};
  background: ${({ theme, $tone }) => ($tone === "inverse" ? "transparent" : theme.colors.surface)};
  color: ${({ theme, $tone }) =>
    $tone === "danger" ? theme.colors.danger : $tone === "inverse" ? theme.colors.sidebarText : theme.colors.textMuted};
  cursor: pointer;
  flex-shrink: 0;
  transition:
    background ${({ theme }) => theme.transitions.fast},
    color ${({ theme }) => theme.transitions.fast};

  &:hover:not(:disabled) {
    background: ${({ theme, $tone }) => ($tone === "inverse" ? theme.colors.sidebarHover : theme.colors.primarySoft)};
    color: ${({ theme, $tone }) =>
      $tone === "danger" ? theme.colors.danger : $tone === "inverse" ? "#fff" : theme.colors.primaryText};
  }
  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;
