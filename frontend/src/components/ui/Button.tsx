"use client";

import { forwardRef, type ButtonHTMLAttributes } from "react";
import styled, { css, keyframes } from "styled-components";

type Variant = "primary" | "outline" | "ghost" | "danger";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
  fullWidth?: boolean;
}

/** Botão em pílula. Em carregamento fica desabilitado e anuncia o estado para leitores de tela. */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "primary", loading = false, fullWidth = false, disabled, children, type = "button", ...rest },
  ref,
) {
  return (
    <StyledButton
      ref={ref}
      type={type}
      $variant={variant}
      $fullWidth={fullWidth}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading && <Spinner aria-hidden="true" />}
      {children}
    </StyledButton>
  );
});

const spin = keyframes`to { transform: rotate(360deg); }`;

const Spinner = styled.span`
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid currentColor;
  border-right-color: transparent;
  animation: ${spin} 0.7s linear infinite;
`;

const variants = {
  primary: css`
    background: ${({ theme }) => theme.colors.primary};
    color: ${({ theme }) => theme.colors.onPrimary};
    border-color: ${({ theme }) => theme.colors.primary};
    &:hover:not(:disabled) {
      background: ${({ theme }) => theme.colors.primaryHover};
      border-color: ${({ theme }) => theme.colors.primaryHover};
    }
    &:active:not(:disabled) {
      background: ${({ theme }) => theme.colors.primaryActive};
    }
  `,
  outline: css`
    background: transparent;
    color: ${({ theme }) => theme.colors.primaryText};
    border-color: ${({ theme }) => theme.colors.primary};
    &:hover:not(:disabled) {
      background: ${({ theme }) => theme.colors.primarySoft};
    }
  `,
  ghost: css`
    background: transparent;
    color: ${({ theme }) => theme.colors.textMuted};
    border-color: transparent;
    &:hover:not(:disabled) {
      background: ${({ theme }) => theme.colors.surfaceMuted};
      color: ${({ theme }) => theme.colors.text};
    }
  `,
  danger: css`
    background: ${({ theme }) => theme.colors.danger};
    color: ${({ theme }) => theme.colors.onPrimary};
    border-color: ${({ theme }) => theme.colors.danger};
  `,
};

const StyledButton = styled.button<{ $variant: Variant; $fullWidth: boolean }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: ${({ theme }) => theme.space[2]};
  min-height: 42px;
  padding: ${({ theme }) => `${theme.space[2]} ${theme.space[6]}`};
  width: ${({ $fullWidth }) => ($fullWidth ? "100%" : "auto")};
  border: 1px solid transparent;
  border-radius: ${({ theme }) => theme.radii.pill};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  font-size: ${({ theme }) => theme.fontSizes.md};
  cursor: pointer;
  transition:
    background ${({ theme }) => theme.transitions.fast},
    border-color ${({ theme }) => theme.transitions.fast};
  ${({ $variant }) => variants[$variant]}

  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;
