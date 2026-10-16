"use client";

import { Search } from "lucide-react";
import { useEffect, useState, type InputHTMLAttributes } from "react";
import styled from "styled-components";
import { controlStyles } from "./fields";

interface SearchInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "onChange" | "value"> {
  value: string;
  onChange: (value: string) => void;
  /** Espera (ms) depois da última tecla antes de avisar a mudança. */
  debounceMs?: number;
  label: string;
}

/** Campo de busca com atraso (evita uma consulta por tecla). */
export function SearchInput({ value, onChange, debounceMs = 300, label, ...rest }: SearchInputProps) {
  const [draft, setDraft] = useState(value);

  useEffect(() => {
    if (draft === value) return;
    const timer = setTimeout(() => onChange(draft), debounceMs);
    return () => clearTimeout(timer);
  }, [draft, value, debounceMs, onChange]);

  return (
    <Box>
      <Icon size={16} aria-hidden="true" />
      <Input type="search" aria-label={label} value={draft} onChange={(e) => setDraft(e.target.value)} {...rest} />
    </Box>
  );
}

const Box = styled.div`
  position: relative;
  flex: 1;
  min-width: 220px;
  max-width: 420px;
`;

const Icon = styled(Search)`
  position: absolute;
  left: ${({ theme }) => theme.space[4]};
  top: 50%;
  transform: translateY(-50%);
  color: ${({ theme }) => theme.colors.textMuted};
  pointer-events: none;
`;

const Input = styled.input<{ $invalid?: boolean }>`
  ${controlStyles}
  padding-left: ${({ theme }) => theme.space[10]};
`;
