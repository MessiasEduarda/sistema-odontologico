"use client";

import { Check, ChevronDown, Paperclip } from "lucide-react";
import {
  forwardRef,
  useCallback,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type InputHTMLAttributes,
  type KeyboardEvent,
  type ReactNode,
  type TextareaHTMLAttributes,
} from "react";
import styled, { css } from "styled-components";
import { ListEmpty, ListOption, ListPanel, popoverMode, useAnchoredList, useClickOutside } from "./listbox";

/** Estilo base compartilhado por campo de texto, seleção e área de texto. */
export const controlStyles = css<{ $invalid?: boolean }>`
  width: 100%;
  min-height: 44px;
  padding: ${({ theme }) => `${theme.space[2]} ${theme.space[5]}`};
  border-radius: ${({ theme }) => theme.radii.pill};
  border: 1px solid ${({ theme, $invalid }) => ($invalid ? theme.colors.danger : theme.colors.border)};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.text};
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
    cursor: not-allowed;
  }
`;

interface WrapperProps {
  id: string;
  label: string;
  error?: string;
  hint?: ReactNode;
  required?: boolean;
  children: ReactNode;
}

/** Rótulo + dica + erro ligados ao controle por ARIA. */
export function FieldWrapper({ id, label, error, hint, required, children }: WrapperProps) {
  return (
    <Field>
      <Label htmlFor={id}>
        {label}
        {required && <Required aria-hidden="true"> *</Required>}
      </Label>
      {children}
      {hint && !error && <Hint id={`${id}-hint`}>{hint}</Hint>}
      {error && (
        <ErrorText id={`${id}-error`} role="alert">
          {error}
        </ErrorText>
      )}
    </Field>
  );
}

export function describedBy(id: string, error?: string, hint?: ReactNode) {
  return [hint && !error ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean).join(" ") || undefined;
}

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectFieldProps {
  label: string;
  value: string;
  /** Mesmo formato do evento nativo (`e.target.value`), para trocar o `<select>` sem mudar quem usa. */
  onChange?: (event: { target: { value: string; name?: string } }) => void;
  options: readonly SelectOption[];
  /** Opção vazia exibida no topo (ex.: "Todas", "Selecione…"). Escolhê-la limpa o valor. */
  placeholder?: string;
  error?: string;
  hint?: ReactNode;
  required?: boolean;
  disabled?: boolean;
  id?: string;
  name?: string;
  style?: CSSProperties;
  "aria-label"?: string;
}

/**
 * Seleção com lista personalizada (mesmo visual do sistema em qualquer navegador). Segue o padrão ARIA de combobox
 * "somente seleção": setas, Home/End, Enter/Espaço, Esc, Tab e busca por letra inicial funcionam pelo teclado.
 */
export function SelectField({
  label,
  value,
  onChange,
  options,
  placeholder,
  error,
  hint,
  required,
  disabled,
  id,
  name,
  style,
  "aria-label": ariaLabel,
}: SelectFieldProps) {
  const generated = useId();
  const selectId = id ?? generated;
  const listId = `${selectId}-list`;
  const triggerRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const typed = useRef({ text: "", at: 0 });
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);

  const items = useMemo<SelectOption[]>(
    () => (placeholder !== undefined ? [{ value: "", label: placeholder }, ...options] : [...options]),
    [placeholder, options],
  );
  const selectedIndex = items.findIndex((o) => o.value === value);
  const selected = selectedIndex >= 0 ? items[selectedIndex] : undefined;
  const showsPlaceholder = !selected || (selected.value === "" && placeholder !== undefined);

  useAnchoredList(open, triggerRef, listRef);
  const close = useCallback(() => setOpen(false), []);
  useClickOutside(open, [triggerRef, listRef], close);

  // Mantém a opção ativa visível ao navegar pelo teclado.
  useEffect(() => {
    if (!open) return;
    listRef.current?.querySelector<HTMLElement>(`[data-index="${active}"]`)?.scrollIntoView?.({ block: "nearest" });
  }, [open, active]);

  const enabledFrom = (start: number, step: 1 | -1) => {
    for (let i = start; i >= 0 && i < items.length; i += step) if (!items[i]?.disabled) return i;
    return -1;
  };

  const openList = () => {
    if (disabled) return;
    setActive(selectedIndex >= 0 ? selectedIndex : Math.max(0, enabledFrom(0, 1)));
    setOpen(true);
  };

  const choose = (index: number) => {
    const option = items[index];
    if (!option || option.disabled) return;
    setOpen(false);
    triggerRef.current?.focus();
    if (option.value !== value) onChange?.({ target: { value: option.value, name } });
  };

  const typeahead = (key: string) => {
    const now = Date.now();
    typed.current = { text: now - typed.current.at < 700 ? typed.current.text + key : key, at: now };
    const term = typed.current.text.toLocaleLowerCase("pt-BR");
    const from = open ? active : Math.max(selectedIndex, 0);
    const order = [...items.keys()].map((k) => (from + 1 + k) % items.length);
    const match = order.find((i) => !items[i]?.disabled && items[i]?.label.toLocaleLowerCase("pt-BR").startsWith(term));
    if (match === undefined) return;
    if (open) setActive(match);
    else if (items[match]?.value !== value) onChange?.({ target: { value: items[match]?.value ?? "", name } });
  };

  const onKeyDown = (e: KeyboardEvent<HTMLButtonElement>) => {
    switch (e.key) {
      case "ArrowDown":
      case "ArrowUp": {
        e.preventDefault();
        if (!open) return openList();
        const step = e.key === "ArrowDown" ? 1 : -1;
        const next = enabledFrom(active + step, step);
        if (next >= 0) setActive(next);
        return;
      }
      case "Home":
      case "End":
        if (!open) return;
        e.preventDefault();
        setActive(e.key === "Home" ? enabledFrom(0, 1) : enabledFrom(items.length - 1, -1));
        return;
      case "Enter":
      case " ":
        e.preventDefault();
        if (open) choose(active);
        else openList();
        return;
      case "Escape":
        if (open) {
          e.preventDefault();
          e.stopPropagation(); // não fecha o modal junto
          setOpen(false);
        }
        return;
      case "Tab":
        if (open) setOpen(false);
        return;
      default:
        if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) typeahead(e.key);
    }
  };

  return (
    <FieldWrapper id={selectId} label={label} error={error} hint={hint} required={required}>
      <SelectBox style={style}>
        <Trigger
          ref={triggerRef}
          id={selectId}
          type="button"
          role="combobox"
          aria-haspopup="listbox"
          aria-expanded={open}
          aria-controls={listId}
          aria-activedescendant={open ? `${listId}-${active}` : undefined}
          aria-invalid={error ? true : undefined}
          aria-required={required || undefined}
          aria-describedby={describedBy(selectId, error, hint)}
          aria-label={ariaLabel}
          disabled={disabled}
          $invalid={Boolean(error)}
          $open={open}
          onClick={() => (open ? setOpen(false) : openList())}
          onKeyDown={onKeyDown}
        >
          <TriggerText $placeholder={showsPlaceholder}>{selected?.label ?? placeholder ?? "Selecione…"}</TriggerText>
          <Chevron aria-hidden="true" size={16} $open={open} />
        </Trigger>
        {name && <input type="hidden" name={name} value={value} />}
        {open && (
          <ListPanel
            ref={listRef}
            id={listId}
            role="listbox"
            aria-labelledby={selectId}
            popover={popoverMode()}
            tabIndex={-1}
          >
            {items.length === 0 && <ListEmpty>Nenhuma opção disponível.</ListEmpty>}
            {items.map((o, i) => (
              <ListOption
                key={`${o.value}-${i}`}
                id={`${listId}-${i}`}
                data-index={i}
                role="option"
                aria-selected={o.value === value}
                aria-disabled={o.disabled || undefined}
                $active={i === active}
                onMouseEnter={() => !o.disabled && setActive(i)}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => choose(i)}
              >
                <span>{o.label}</span>
                {o.value === value && <Check size={16} aria-hidden="true" />}
              </ListOption>
            ))}
          </ListPanel>
        )}
      </SelectBox>
    </FieldWrapper>
  );
}

export interface TextAreaFieldProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  error?: string;
  hint?: ReactNode;
}

export const TextAreaField = forwardRef<HTMLTextAreaElement, TextAreaFieldProps>(function TextAreaField(
  { label, error, hint, id, required, rows = 4, ...rest },
  ref,
) {
  const generated = useId();
  const areaId = id ?? generated;
  return (
    <FieldWrapper id={areaId} label={label} error={error} hint={hint} required={required}>
      <TextArea
        ref={ref}
        id={areaId}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(areaId, error, hint)}
        required={required}
        $invalid={Boolean(error)}
        {...rest}
      />
    </FieldWrapper>
  );
});

export interface CheckboxFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "type"> {
  label: ReactNode;
}

/** Caixa de marcar no visual do sistema: o input nativo fica invisível (mas acessível) e a caixa é desenhada. */
export const CheckboxField = forwardRef<HTMLInputElement, CheckboxFieldProps>(function CheckboxField(
  { label, id, ...rest },
  ref,
) {
  const generated = useId();
  const boxId = id ?? generated;
  return (
    <CheckLabel htmlFor={boxId}>
      <NativeCheck ref={ref} id={boxId} type="checkbox" {...rest} />
      <CheckBox aria-hidden="true">
        <Check size={14} strokeWidth={3} />
      </CheckBox>
      <span>{label}</span>
    </CheckLabel>
  );
});

export interface FileFieldProps {
  label: string;
  accept?: string;
  hint?: ReactNode;
  error?: string;
  /** Nome do arquivo escolhido (ou null). */
  fileName: string | null;
  onChange: (file: File | null) => void;
  buttonLabel?: string;
}

/**
 * Escolha de arquivo no visual do sistema: botão em pílula + nome do arquivo, no lugar do controle nativo
 * ("Choose File"), que muda de navegador para navegador e não segue o idioma da tela.
 */
export const FileField = forwardRef<HTMLInputElement, FileFieldProps>(function FileField(
  { label, accept, hint, error, fileName, onChange, buttonLabel = "Escolher arquivo" },
  ref,
) {
  const id = useId();
  const inner = useRef<HTMLInputElement | null>(null);
  const setRefs = (el: HTMLInputElement | null) => {
    inner.current = el;
    if (typeof ref === "function") ref(el);
    else if (ref) ref.current = el;
  };
  return (
    <FieldWrapper id={id} label={label} hint={hint} error={error}>
      <FilePicker $invalid={Boolean(error)}>
        <input
          ref={setRefs}
          id={id}
          type="file"
          accept={accept}
          className="sr-only"
          aria-describedby={describedBy(id, error, hint)}
          onChange={(e) => onChange(e.target.files?.[0] ?? null)}
        />
        <FileButton type="button" onClick={() => inner.current?.click()}>
          <Paperclip size={16} aria-hidden /> {buttonLabel}
        </FileButton>
        <FileName $empty={!fileName} title={fileName ?? undefined}>
          {fileName ?? "Nenhum arquivo escolhido"}
        </FileName>
      </FilePicker>
    </FieldWrapper>
  );
});

export interface ColorFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
}

/** Escolha de cor no visual do sistema: amostra redonda + código da cor; o seletor do navegador abre ao clicar. */
export function ColorField({ label, value, onChange }: ColorFieldProps) {
  const id = useId();
  return (
    <FieldWrapper id={id} label={label}>
      <ColorPicker htmlFor={id}>
        <input id={id} type="color" className="sr-only" value={value} onChange={(e) => onChange(e.target.value)} />
        <Swatch aria-hidden="true" style={{ background: value }} />
        <span>{value.toUpperCase()}</span>
      </ColorPicker>
    </FieldWrapper>
  );
}

const Field = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[2]};
  align-content: start;
  min-width: 0;
`;

const Label = styled.label`
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  font-size: ${({ theme }) => theme.fontSizes.md};
`;

const Required = styled.span`
  color: ${({ theme }) => theme.colors.danger};
`;

const Hint = styled.p`
  font-size: ${({ theme }) => theme.fontSizes.sm};
  color: ${({ theme }) => theme.colors.textMuted};
`;

const ErrorText = styled.p`
  font-size: ${({ theme }) => theme.fontSizes.sm};
  color: ${({ theme }) => theme.colors.danger};
`;

const SelectBox = styled.div`
  position: relative;
`;

const Trigger = styled.button<{ $invalid?: boolean; $open: boolean }>`
  ${controlStyles}
  display: flex;
  align-items: center;
  text-align: left;
  font: inherit;
  padding-right: ${({ theme }) => theme.space[10]};
  cursor: pointer;

  ${({ $open, theme }) =>
    $open &&
    css`
      border-color: ${theme.colors.primary};
      box-shadow: ${theme.shadows.focus};
    `}
`;

const TriggerText = styled.span<{ $placeholder: boolean }>`
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: ${({ theme, $placeholder }) => ($placeholder ? theme.colors.textSubtle : "inherit")};
`;

const Chevron = styled(ChevronDown)<{ $open: boolean }>`
  position: absolute;
  right: ${({ theme }) => theme.space[4]};
  top: 50%;
  transform: translateY(-50%) rotate(${({ $open }) => ($open ? "180deg" : "0deg")});
  transition: transform ${({ theme }) => theme.transitions.fast};
  pointer-events: none;
  color: ${({ theme }) => theme.colors.textMuted};
`;

const TextArea = styled.textarea<{ $invalid?: boolean }>`
  ${controlStyles}
  border-radius: ${({ theme }) => theme.radii.lg};
  padding: ${({ theme }) => `${theme.space[3]} ${theme.space[5]}`};
  resize: vertical;
  line-height: 1.5;
`;

const CheckLabel = styled.label`
  position: relative;
  display: inline-flex;
  align-items: flex-start;
  gap: ${({ theme }) => theme.space[3]};
  cursor: pointer;
  font-size: ${({ theme }) => theme.fontSizes.md};
  line-height: 1.4;
`;

/** Input nativo: invisível, mas continua recebendo foco, teclado (Espaço) e leitor de tela. */
const NativeCheck = styled.input`
  position: absolute;
  opacity: 0;
  width: 20px;
  height: 20px;
  margin: 0;
  cursor: pointer;
`;

const CheckBox = styled.span`
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border: 1.5px solid ${({ theme }) => theme.colors.borderStrong};
  border-radius: 6px;
  background: ${({ theme }) => theme.colors.surface};
  color: transparent;
  transition:
    background ${({ theme }) => theme.transitions.fast},
    border-color ${({ theme }) => theme.transitions.fast};

  ${CheckLabel}:hover & {
    border-color: ${({ theme }) => theme.colors.primary};
  }
  ${NativeCheck}:checked + & {
    background: ${({ theme }) => theme.colors.primary};
    border-color: ${({ theme }) => theme.colors.primary};
    color: ${({ theme }) => theme.colors.onPrimary};
  }
  ${NativeCheck}:focus-visible + & {
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
  ${NativeCheck}:disabled + & {
    opacity: 0.5;
  }
`;

const FilePicker = styled.div<{ $invalid?: boolean }>`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space[3]};
  min-height: 44px;
  padding: 4px ${({ theme }) => theme.space[4]} 4px 4px;
  border: 1px solid ${({ theme, $invalid }) => ($invalid ? theme.colors.danger : theme.colors.border)};
  border-radius: ${({ theme }) => theme.radii.pill};
  background: ${({ theme }) => theme.colors.surface};
  min-width: 0;

  &:focus-within {
    border-color: ${({ theme }) => theme.colors.primary};
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
`;

const FileButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: ${({ theme }) => theme.space[2]};
  flex-shrink: 0;
  min-height: 34px;
  padding: 0 ${({ theme }) => theme.space[4]};
  border: 0;
  border-radius: ${({ theme }) => theme.radii.pill};
  background: ${({ theme }) => theme.colors.primarySoft};
  color: ${({ theme }) => theme.colors.primaryText};
  font-size: ${({ theme }) => theme.fontSizes.sm};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  cursor: pointer;
  transition: background ${({ theme }) => theme.transitions.fast};

  &:hover {
    filter: brightness(0.96);
  }
  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
`;

const FileName = styled.span<{ $empty: boolean }>`
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: ${({ theme }) => theme.fontSizes.md};
  color: ${({ theme, $empty }) => ($empty ? theme.colors.textSubtle : theme.colors.text)};
`;

const ColorPicker = styled.label`
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: ${({ theme }) => theme.space[3]};
  min-height: 44px;
  padding: 4px ${({ theme }) => theme.space[5]} 4px 6px;
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radii.pill};
  background: ${({ theme }) => theme.colors.surface};
  cursor: pointer;
  font-variant-numeric: tabular-nums;

  &:hover {
    border-color: ${({ theme }) => theme.colors.primary};
  }
  &:focus-within {
    border-color: ${({ theme }) => theme.colors.primary};
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
`;

const Swatch = styled.span`
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid rgba(0, 0, 0, 0.12);
  flex-shrink: 0;
`;

/** Grade de formulário responsiva (1 coluna no celular, N a partir do tablet). */
export const FormGrid = styled.div<{ $columns?: number }>`
  display: grid;
  gap: ${({ theme }) => theme.space[4]};
  grid-template-columns: 1fr;

  @media (min-width: ${({ theme }) => theme.breakpoints.md}px) {
    grid-template-columns: repeat(${({ $columns = 2 }) => $columns}, minmax(0, 1fr));
  }
`;

/** Ocupa a linha inteira dentro de FormGrid. */
export const FullRow = styled.div`
  grid-column: 1 / -1;
`;
