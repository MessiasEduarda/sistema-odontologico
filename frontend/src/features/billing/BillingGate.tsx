"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { CreditCard, LogOut, RefreshCw } from "lucide-react";
import Link from "next/link";
import { type ReactNode, useEffect, useId, useRef, useState } from "react";
import styled from "styled-components";
import { Button } from "@/components/ui/Button";
import { useLogout, useMe } from "@/features/auth/hooks";
import { BILLING_BLOCKED_EVENT } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Subscription } from "@/lib/api/types";
import { formatDate, formatMoney, parseDateOnly } from "@/lib/format";

export const SUBSCRIPTION_KEY = ["clinic-subscription"] as const;

/** Espera depois de entrar até o aviso de vencimento aparecer. */
const DELAY_MS = 2000;

/** O aviso (ainda sem bloqueio) aparece no máximo uma vez por dia em cada navegador. */
function reminderKey(tenantId: string) {
  return `odonto.billing.reminded.${tenantId}`;
}

function todayKey() {
  const d = new Date();
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;
}

function alreadyReminded(tenantId: string): boolean {
  try {
    return window.localStorage.getItem(reminderKey(tenantId)) === todayKey();
  } catch {
    return false;
  }
}

function markReminded(tenantId: string) {
  try {
    window.localStorage.setItem(reminderKey(tenantId), todayKey());
  } catch {
    // Sem armazenamento: o aviso volta a aparecer na próxima visita, o que é aceitável.
  }
}

/** Véspera do bloqueio (último dia em que o sistema ainda funciona). */
function lastDay(blockedFrom: string): string {
  const d = parseDateOnly(blockedFrom);
  d.setDate(d.getDate() - 1);
  return formatDate(d.toISOString());
}

/**
 * Cobrança da assinatura. Vencida dentro da tolerância: aviso (uma vez por dia) com o botão de pagamento. Tolerância
 * esgotada: tela de bloqueio que não fecha; o servidor também recusa tudo (402) até o pagamento ser registrado.
 */
export function BillingGate() {
  const { data: me } = useMe();
  const queryClient = useQueryClient();
  const tenantId = me?.activeClinic?.id;
  const sub = useQuery({
    queryKey: [...SUBSCRIPTION_KEY, tenantId],
    queryFn: api.clinic.subscription,
    enabled: Boolean(tenantId),
    retry: false,
    refetchOnWindowFocus: true,
    refetchInterval: 5 * 60_000,
  });
  const [reminderOpen, setReminderOpen] = useState(false);
  const state = sub.data?.billing.state ?? "OK";

  // Qualquer resposta 402 (bloqueio por falta de pagamento) atualiza a situação na hora.
  const stateRef = useRef(state);
  useEffect(() => {
    stateRef.current = state;
  }, [state]);
  useEffect(() => {
    const onBlocked = () => {
      if (stateRef.current !== "BLOCKED") void queryClient.invalidateQueries({ queryKey: SUBSCRIPTION_KEY });
    };
    window.addEventListener(BILLING_BLOCKED_EVENT, onBlocked);
    return () => window.removeEventListener(BILLING_BLOCKED_EVENT, onBlocked);
  }, [queryClient]);

  // Acesso liberado (pagamento registrado): recarrega o que falhou enquanto estava bloqueado.
  const previous = useRef(state);
  useEffect(() => {
    if (previous.current === "BLOCKED" && state !== "BLOCKED") void queryClient.invalidateQueries();
    previous.current = state;
  }, [state, queryClient]);

  useEffect(() => {
    if (state !== "DUE" || !tenantId || alreadyReminded(tenantId)) return;
    const timer = window.setTimeout(() => setReminderOpen(true), DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [state, tenantId]);

  if (!sub.data || !tenantId) return null;
  if (state === "BLOCKED") {
    return <BlockedDialog sub={sub.data} checking={sub.isFetching} onCheck={() => void sub.refetch()} />;
  }
  if (state === "DUE" && reminderOpen) {
    return (
      <DueDialog
        sub={sub.data}
        onClose={() => {
          markReminded(tenantId);
          setReminderOpen(false);
        }}
      />
    );
  }
  return null;
}

function PayButton({ sub }: { sub: Subscription }) {
  if (!sub.paymentUrl) {
    return <Muted>Fale com o suporte para receber o link de pagamento.</Muted>;
  }
  return (
    <Button onClick={() => window.open(sub.paymentUrl ?? "", "_blank", "noopener,noreferrer")}>
      <CreditCard size={18} aria-hidden /> Pagar agora
    </Button>
  );
}

function DueDialog({ sub, onClose }: { sub: Subscription; onClose: () => void }) {
  const { billing } = sub;
  return (
    <Frame
      title="Pagamento da assinatura em atraso"
      subtitle={`Venceu em ${formatDate(billing.dueDate)}`}
      onClose={onClose}
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Lembrar mais tarde
          </Button>
          <PayButton sub={sub} />
        </>
      }
    >
      <p>
        Para continuar usando o sistema sem interrupção, faça o pagamento da assinatura{" "}
        <strong>
          {sub.planName} ({formatMoney(sub.priceMonthly)}/mês)
        </strong>
        .
      </p>
      {billing.blockedFrom && (
        <p>
          Se o pagamento não for identificado até <strong>{lastDay(billing.blockedFrom)}</strong>, o acesso da clínica
          será suspenso até a regularização.
        </p>
      )}
    </Frame>
  );
}

function BlockedDialog({ sub, checking, onCheck }: { sub: Subscription; checking: boolean; onCheck: () => void }) {
  const { data: me } = useMe();
  const logout = useLogout();
  return (
    <Frame
      title="Acesso suspenso"
      subtitle={`Assinatura vencida desde ${formatDate(sub.billing.dueDate)}`}
      footer={
        <>
          <Button variant="ghost" onClick={() => logout.mutate()} loading={logout.isPending}>
            <LogOut size={18} aria-hidden /> Sair
          </Button>
          <Button variant="outline" onClick={onCheck} loading={checking}>
            <RefreshCw size={18} aria-hidden /> Já paguei
          </Button>
          <PayButton sub={sub} />
        </>
      }
    >
      <p>
        O sistema está indisponível porque o pagamento da assinatura{" "}
        <strong>
          {sub.planName} ({formatMoney(sub.priceMonthly)}/mês)
        </strong>{" "}
        não foi identificado.
      </p>
      <p>
        Seus dados continuam guardados e nada foi apagado. Assim que o pagamento for confirmado, o acesso volta
        normalmente. Se já pagou, aguarde a confirmação e clique em “Já paguei”.
      </p>
      {(me?.clinics.length ?? 0) > 1 && (
        <p>
          <SwitchLink href="/selecionar-clinica">Trocar de clínica</SwitchLink>
        </p>
      )}
    </Frame>
  );
}

/** Moldura do aviso: cabeçalho na cor da marca. Sem `onClose`, não fecha (nem com Esc nem clicando fora). */
function Frame({
  title,
  subtitle,
  children,
  footer,
  onClose,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer: ReactNode;
  onClose?: () => void;
}) {
  const titleId = useId();
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    ref.current?.focus();
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);

  useEffect(() => {
    if (!onClose) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <Overlay onMouseDown={(e) => onClose && e.target === e.currentTarget && onClose()}>
      <Dialog ref={ref} role="alertdialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}>
        <Header>
          <h2 id={titleId}>{title}</h2>
          <p>{subtitle}</p>
        </Header>
        <Body>{children}</Body>
        <Footer>{footer}</Footer>
      </Dialog>
    </Overlay>
  );
}

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  z-index: ${({ theme }) => theme.zIndex.modal};
  display: grid;
  place-items: center;
  padding: ${({ theme }) => theme.space[4]};
  background: rgba(20, 20, 20, 0.6);
`;

const Dialog = styled.div`
  width: 100%;
  max-width: 520px;
  max-height: calc(100vh - 2rem);
  overflow: auto;
  border-radius: 10px;
  background: ${({ theme }) => theme.colors.surface};
  box-shadow:
    0 4px 12px rgba(20, 20, 20, 0.15),
    0 30px 80px rgba(20, 20, 20, 0.35);

  &:focus {
    outline: none;
  }
`;

const Header = styled.header`
  padding: ${({ theme }) => `${theme.space[6]} ${theme.space[6]} ${theme.space[5]}`};
  background: ${({ theme }) => theme.colors.primaryText};
  color: ${({ theme }) => theme.colors.onPrimary};

  h2 {
    font-family: ${({ theme }) => theme.fonts.display};
    font-size: ${({ theme }) => theme.fontSizes.xl};
    font-weight: ${({ theme }) => theme.fontWeights.bold};
  }
  p {
    margin-top: ${({ theme }) => theme.space[1]};
    font-size: ${({ theme }) => theme.fontSizes.sm};
    color: ${({ theme }) => theme.colors.primarySoft};
  }
`;

const Body = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[3]};
  padding: ${({ theme }) => theme.space[6]};
  font-size: ${({ theme }) => theme.fontSizes.md};
  line-height: 1.55;
`;

const Footer = styled.footer`
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: ${({ theme }) => theme.space[2]};
  padding: ${({ theme }) => `0 ${theme.space[6]} ${theme.space[6]}`};
`;

const Muted = styled.span`
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.sm};
`;

const SwitchLink = styled(Link)`
  color: ${({ theme }) => theme.colors.primaryText};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
`;
