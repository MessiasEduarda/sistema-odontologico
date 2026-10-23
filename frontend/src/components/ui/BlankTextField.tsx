"use client";

import { Fragment, useId, useState, type ReactNode } from "react";
import styled from "styled-components";
import { Button } from "./Button";
import { FieldWrapper, TextAreaField } from "./fields";

const BLANK = /(_{2,})/;

interface Parsed {
  segments: string[];
  filled: Record<number, string>;
  emitted: string;
}

const parse = (value: string): Parsed => ({ segments: value.split(BLANK), filled: {}, emitted: value });

const compose = (segments: string[], filled: Record<number, string>) =>
  segments.map((s, i) => (i % 2 === 1 && filled[i] ? filled[i] : s)).join("");

interface BlankTextFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  rows?: number;
  maxLength?: number;
  hint?: ReactNode;
  required?: boolean;
  error?: string;
}

export function BlankTextField({
  label,
  value,
  onChange,
  rows = 12,
  maxLength,
  hint,
  required,
  error,
}: BlankTextFieldProps) {
  const id = useId();
  const [state, setState] = useState(() => parse(value));
  const [freeText, setFreeText] = useState(false);
  if (value !== state.emitted) setState(parse(value));

  const blanks = state.segments.filter((_, i) => i % 2 === 1).length;

  if (freeText || blanks === 0) {
    return (
      <div>
        <TextAreaField
          label={label}
          required={required}
          rows={rows}
          maxLength={maxLength}
          value={value}
          hint={hint}
          error={error}
          onChange={(e) => onChange(e.target.value)}
        />
        {freeText && BLANK.test(value) && (
          <Toggle>
            <Button variant="ghost" onClick={() => setFreeText(false)}>
              Voltar a preencher os espaços
            </Button>
          </Toggle>
        )}
      </div>
    );
  }

  const fill = (index: number, text: string) => {
    const filled = { ...state.filled, [index]: text };
    const emitted = compose(state.segments, filled);
    setState({ ...state, filled, emitted });
    onChange(emitted);
  };

  return (
    <div>
      <FieldWrapper
        id={`${id}-1`}
        label={label}
        required={required}
        error={error}
        hint={
          hint ?? "Clique nos espaços destacados para preencher. Os que ficarem vazios saem como linha no documento."
        }
      >
        <FillBox $rows={rows}>
          {state.segments.map((segment, i) => {
            if (i % 2 === 0) return <Fragment key={i}>{segment}</Fragment>;
            const order = (i + 1) / 2;
            const text = state.filled[i] ?? "";
            return (
              <BlankInput
                key={i}
                id={`${id}-${order}`}
                aria-label={`Espaço ${order} de ${blanks}`}
                value={text}
                style={{ width: `${Math.max(segment.length, text.length) + 2}ch` }}
                onChange={(e) => fill(i, e.target.value.replace(/\n/g, " "))}
              />
            );
          })}
        </FillBox>
      </FieldWrapper>
      <Toggle>
        <Button variant="ghost" onClick={() => setFreeText(true)}>
          Editar texto completo
        </Button>
      </Toggle>
    </div>
  );
}

const FillBox = styled.div<{ $rows: number }>`
  min-height: ${({ $rows }) => `calc(${$rows} * 1.5em + 24px)`};
  padding: ${({ theme }) => `${theme.space[3]} ${theme.space[5]}`};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radii.lg};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.text};
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 2;

  &:focus-within {
    border-color: ${({ theme }) => theme.colors.primary};
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
`;

const BlankInput = styled.input`
  max-width: 100%;
  min-width: 4ch;
  height: 1.7em;
  margin: 0 2px;
  padding: 0 6px;
  border: 0;
  border-bottom: 1.5px dashed ${({ theme }) => theme.colors.primary};
  border-radius: 4px 4px 0 0;
  background: ${({ theme }) => theme.colors.primarySoft};
  color: ${({ theme }) => theme.colors.text};
  font: inherit;
  line-height: 1.5;
  vertical-align: baseline;

  &:focus {
    outline: none;
    border-bottom-style: solid;
    background: ${({ theme }) => theme.colors.surface};
    box-shadow: 0 0 0 2px ${({ theme }) => theme.colors.primarySoft};
  }
`;

const Toggle = styled.div`
  display: flex;
  justify-content: flex-end;
  margin-top: ${({ theme }) => theme.space[1]};
`;
