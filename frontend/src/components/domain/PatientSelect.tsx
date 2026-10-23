"use client";

import { useQuery } from "@tanstack/react-query";
import { X } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import styled from "styled-components";
import { controlStyles, FieldWrapper } from "@/components/ui/fields";
import { ListEmpty, ListOption, ListPanel, popoverMode, useAnchoredList } from "@/components/ui/listbox";
import { api } from "@/lib/api/endpoints";
import { formatCpf, formatPhone } from "@/lib/format";

interface PatientSelectProps {
  label?: string;
  value: { id: string; name: string } | null;
  onChange: (patient: { id: string; name: string } | null) => void;
  error?: string;
  required?: boolean;
  disabled?: boolean;
}

export function PatientSelect({ label = "Paciente", value, onChange, error, required, disabled }: PatientSelectProps) {
  const id = useId();
  const listId = `${id}-list`;
  const [term, setTerm] = useState("");
  const [debounced, setDebounced] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  useEffect(() => {
    const t = setTimeout(() => setDebounced(term), 250);
    return () => clearTimeout(t);
  }, [term]);

  const { data, isFetching } = useQuery({
    queryKey: ["patients", "select", debounced],
    queryFn: () => api.patients.search({ q: debounced, active: true, size: 8 }),
    enabled: open && debounced.trim().length >= 2,
  });
  const options = data?.items ?? [];
  const showList = open && debounced.trim().length >= 2 && !value;
  useAnchoredList(showList, inputRef, listRef);

  if (value) {
    return (
      <FieldWrapper id={id} label={label} error={error} required={required}>
        <Selected>
          <span id={id}>{value.name}</span>
          {!disabled && (
            <ClearButton type="button" aria-label={`Trocar paciente (${value.name})`} onClick={() => onChange(null)}>
              <X size={16} aria-hidden />
            </ClearButton>
          )}
        </Selected>
      </FieldWrapper>
    );
  }

  const choose = (index: number) => {
    const option = options[index];
    if (!option) return;
    onChange({ id: option.id, name: option.fullName });
    setTerm("");
    setOpen(false);
  };

  return (
    <FieldWrapper
      id={id}
      label={label}
      error={error}
      required={required}
      hint="Digite ao menos 2 letras do nome, CPF ou telefone."
    >
      <Box>
        <Input
          ref={inputRef}
          id={id}
          role="combobox"
          aria-expanded={open && options.length > 0}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={open && options[active] ? `${listId}-${active}` : undefined}
          aria-invalid={error ? true : undefined}
          autoComplete="off"
          placeholder="Buscar paciente…"
          value={term}
          disabled={disabled}
          $invalid={Boolean(error)}
          onChange={(e) => {
            setTerm(e.target.value);
            setOpen(true);
            setActive(0);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          onKeyDown={(e) => {
            if (e.key === "ArrowDown") {
              e.preventDefault();
              setActive((a) => Math.min(a + 1, options.length - 1));
            } else if (e.key === "ArrowUp") {
              e.preventDefault();
              setActive((a) => Math.max(a - 1, 0));
            } else if (e.key === "Enter" && open && options.length > 0) {
              e.preventDefault();
              choose(active);
            } else if (e.key === "Escape") {
              setOpen(false);
            }
          }}
        />
        {showList && (
          <ListPanel
            ref={listRef}
            id={listId}
            role="listbox"
            aria-label="Pacientes encontrados"
            popover={popoverMode()}
          >
            {isFetching && options.length === 0 && <ListEmpty>Buscando…</ListEmpty>}
            {!isFetching && options.length === 0 && <ListEmpty>Nenhum paciente encontrado.</ListEmpty>}
            {options.map((p, i) => (
              <ListOption
                key={p.id}
                id={`${listId}-${i}`}
                role="option"
                aria-selected={i === active}
                $active={i === active}
                onMouseDown={(e) => {
                  e.preventDefault();
                  choose(i);
                }}
                onMouseEnter={() => setActive(i)}
              >
                <PatientLine>
                  <strong>{p.fullName}</strong>
                  <small>
                    {[p.cpf ? formatCpf(p.cpf) : null, p.mobile ? formatPhone(p.mobile) : null]
                      .filter(Boolean)
                      .join(" · ")}
                  </small>
                </PatientLine>
              </ListOption>
            ))}
          </ListPanel>
        )}
      </Box>
    </FieldWrapper>
  );
}

const Box = styled.div`
  position: relative;
`;

const Input = styled.input<{ $invalid?: boolean }>`
  ${controlStyles}
`;

const PatientLine = styled.span`
  display: grid;
  gap: 2px;
`;

const Selected = styled.div<{ $invalid?: boolean }>`
  ${controlStyles}
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: ${({ theme }) => theme.colors.primarySoft};
  border-color: ${({ theme }) => theme.colors.primary};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
`;

const ClearButton = styled.button`
  border: none;
  background: transparent;
  cursor: pointer;
  display: grid;
  place-items: center;
  padding: 4px;
  border-radius: 6px;
  color: ${({ theme }) => theme.colors.textMuted};
  &:hover {
    background: ${({ theme }) => theme.colors.surface};
  }
`;
