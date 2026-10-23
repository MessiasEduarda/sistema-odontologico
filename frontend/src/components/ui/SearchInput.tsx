"use client";

import { Search } from "lucide-react";
import { useEffect, useState, type InputHTMLAttributes } from "react";
import styled from "styled-components";
import { controlStyles } from "./fields";

interface SearchInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "onChange" | "value"> {
  value: string;
  onChange: (value: string) => void;
  debounceMs?: number;
  label: string;
}

const normalize = (text: string) =>
  text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim();

export function useRowSearch<T>(rows: T[] | undefined, text: (row: T) => (string | null | undefined)[]) {
  const [query, setQuery] = useState("");
  const term = normalize(query);
  const filtered =
    term && rows ? rows.filter((row) => text(row).some((value) => value && normalize(value).includes(term))) : rows;
  return { query, setQuery, rows: filtered };
}

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
