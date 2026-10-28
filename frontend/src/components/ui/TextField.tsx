"use client";

import { Eye, EyeOff } from "lucide-react";
import { forwardRef, useId, useState, type InputHTMLAttributes, type ReactNode } from "react";
import styled from "styled-components";

export interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: ReactNode;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, error, hint, id, type, ...rest },
  ref,
) {
  const [visible, setVisible] = useState(false);
  const isPassword = type === "password";
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const hintId = hint ? `${inputId}-hint` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;

  const input = (
    <Input
      ref={ref}
      id={inputId}
      type={isPassword && visible ? "text" : type}
      aria-invalid={error ? true : undefined}
      aria-describedby={[hintId, errorId].filter(Boolean).join(" ") || undefined}
      $invalid={Boolean(error)}
      $withToggle={isPassword}
      data-password-toggle={isPassword || undefined}
      {...rest}
    />
  );

  return (
    <Field>
      <Label htmlFor={inputId}>
        {label}
        {rest.required && <Required aria-hidden="true"> *</Required>}
      </Label>
      {isPassword ? (
        <InputBox>
          {input}
          <Toggle
            type="button"
            onClick={() => setVisible((v) => !v)}
            aria-label={visible ? "Ocultar senha" : "Mostrar senha"}
            aria-pressed={visible}
            aria-controls={inputId}
            title={visible ? "Ocultar senha" : "Mostrar senha"}
            disabled={rest.disabled}
          >
            {visible ? <EyeOff size={18} aria-hidden /> : <Eye size={18} aria-hidden />}
          </Toggle>
        </InputBox>
      ) : (
        input
      )}
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
  align-content: start;
  min-width: 0;
`;

const Label = styled.label`
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  font-size: ${({ theme }) => theme.fontSizes.md};
  color: ${({ theme }) => theme.colors.text};
`;

const Required = styled.span`
  color: ${({ theme }) => theme.colors.danger};
`;

const InputBox = styled.div`
  position: relative;
  min-width: 0;
`;

const Toggle = styled.button`
  position: absolute;
  top: 50%;
  right: ${({ theme }) => theme.space[2]};
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: ${({ theme }) => theme.radii.pill};
  background: transparent;
  color: ${({ theme }) => theme.colors.textMuted};
  cursor: pointer;

  &:hover:not(:disabled) {
    color: ${({ theme }) => theme.colors.text};
  }
  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
  &:disabled {
    cursor: not-allowed;
  }
`;

const Input = styled.input<{ $invalid: boolean; $withToggle?: boolean }>`
  width: 100%;
  min-height: 44px;
  padding: ${({ theme }) => `${theme.space[2]} ${theme.space[5]}`};
  padding-right: ${({ theme, $withToggle }) => ($withToggle ? "48px" : theme.space[5])};
  border-radius: ${({ theme }) => theme.radii.pill};
  border: 1px solid ${({ theme, $invalid }) => ($invalid ? theme.colors.danger : theme.colors.border)};
  background: ${({ theme }) => theme.colors.surface};
  transition:
    border-color ${({ theme }) => theme.transitions.fast},
    box-shadow ${({ theme }) => theme.transitions.fast};

  &::placeholder {
    color: ${({ theme }) => theme.colors.textSubtle};
  }
  &::-ms-reveal,
  &::-ms-clear {
    display: none;
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
  font-size: ${({ theme }) => theme.fontSizes.md};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  color: ${({ theme }) => theme.colors.danger};
`;
