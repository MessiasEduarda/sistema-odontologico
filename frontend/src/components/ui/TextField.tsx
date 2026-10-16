"use client";

import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from "react";
import styled from "styled-components";

export interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: ReactNode;
}

/** Campo com rótulo, dica e mensagem de erro associados por ARIA (leitores de tela anunciam o erro). */
export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, error, hint, id, ...rest },
  ref,
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const hintId = hint ? `${inputId}-hint` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;

  return (
    <Field>
      <Label htmlFor={inputId}>{label}</Label>
      <Input
        ref={ref}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={[hintId, errorId].filter(Boolean).join(" ") || undefined}
        $invalid={Boolean(error)}
        {...rest}
      />
      {hint && !error && <Hint id={hintId}>{hint}</Hint>}
      {error && (
        <ErrorText id={errorId} role="alert">
          {error}
        </ErrorText>
      )}
    </Field>
  );
});

const Field = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[2]};
`;

const Label = styled.label`
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  font-size: ${({ theme }) => theme.fontSizes.md};
  color: ${({ theme }) => theme.colors.text};
`;

const Input = styled.input<{ $invalid: boolean }>`
  width: 100%;
  min-height: 44px;
  padding: ${({ theme }) => `${theme.space[2]} ${theme.space[5]}`};
  border-radius: ${({ theme }) => theme.radii.pill};
  border: 1px solid ${({ theme, $invalid }) => ($invalid ? theme.colors.danger : theme.colors.border)};
  background: ${({ theme }) => theme.colors.surface};
  transition:
    border-color ${({ theme }) => theme.transitions.fast},
    box-shadow ${({ theme }) => theme.transitions.fast};

  &::placeholder {
    color: ${({ theme }) => theme.colors.textSubtle};
  }
  &:hover:not(:disabled) {
    border-color: ${({ theme, $invalid }) => ($invalid ? theme.colors.danger : theme.colors.borderStrong)};
  }
  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.primary};
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceMuted};
    color: ${({ theme }) => theme.colors.textMuted};
  }
`;

const Hint = styled.p`
  font-size: ${({ theme }) => theme.fontSizes.sm};
  color: ${({ theme }) => theme.colors.textMuted};
`;

const ErrorText = styled.p`
  font-size: ${({ theme }) => theme.fontSizes.sm};
  color: ${({ theme }) => theme.colors.danger};
`;
