"use client";

import { CheckCircle2, X, XCircle } from "lucide-react";
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import styled from "styled-components";

type ToastTone = "success" | "error";
interface ToastItem {
  id: number;
  tone: ToastTone;
  message: string;
}

interface ToastApi {
  success: (message: string) => void;
  error: (message: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);

  const remove = useCallback((id: number) => setItems((all) => all.filter((t) => t.id !== id)), []);
  const push = useCallback(
    (tone: ToastTone, message: string) => {
      const id = Date.now() + Math.random();
      setItems((all) => [...all.slice(-3), { id, tone, message }]);
      setTimeout(() => remove(id), 5000);
    },
    [remove],
  );

  const api = useMemo<ToastApi>(() => ({ success: (m) => push("success", m), error: (m) => push("error", m) }), [push]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <Region aria-live="polite" aria-label="Notificações">
        {items.map((t) => (
          <Item key={t.id} $tone={t.tone} role={t.tone === "error" ? "alert" : "status"}>
            {t.tone === "success" ? <CheckCircle2 size={18} aria-hidden /> : <XCircle size={18} aria-hidden />}
            <span>{t.message}</span>
            <Close type="button" aria-label="Fechar notificação" onClick={() => remove(t.id)}>
              <X size={16} aria-hidden />
            </Close>
          </Item>
        ))}
      </Region>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast fora de ToastProvider");
  return ctx;
}

const Region = styled.div`
  position: fixed;
  right: ${({ theme }) => theme.space[4]};
  bottom: ${({ theme }) => theme.space[4]};
  z-index: ${({ theme }) => theme.zIndex.toast};
  display: grid;
  gap: ${({ theme }) => theme.space[2]};
  max-width: calc(100vw - 32px);
`;

const Item = styled.div<{ $tone: ToastTone }>`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space[3]};
  min-width: 260px;
  max-width: 420px;
  padding: ${({ theme }) => `${theme.space[3]} ${theme.space[4]}`};
  border-radius: ${({ theme }) => theme.radii.md};
  background: ${({ theme }) => theme.colors.sidebar};
  color: #fff;
  box-shadow: ${({ theme }) => theme.shadows.overlay};

  svg:first-child {
    color: ${({ $tone }) => ($tone === "success" ? "#7BD3A0" : "#F19A94")};
    flex-shrink: 0;
  }
  span {
    flex: 1;
  }
`;

const Close = styled.button`
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  display: grid;
  place-items: center;
  padding: 4px;
  border-radius: 6px;
  &:hover {
    background: rgba(255, 255, 255, 0.12);
  }
`;
