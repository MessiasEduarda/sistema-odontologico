"use client";

import type { LucideIcon } from "lucide-react";
import { AlertTriangle, Inbox, Lock } from "lucide-react";
import type { ReactNode } from "react";
import styled, { type DefaultTheme } from "styled-components";
import { Button } from "./Button";

/* ---------------------------------------------------------------- Badge */

export type BadgeTone = "neutral" | "primary" | "success" | "warning" | "danger" | "info";

const badgePalette = (theme: DefaultTheme, tone: BadgeTone) =>
  ({
    neutral: { bg: "#EFECE8", fg: theme.colors.textMuted },
    primary: { bg: theme.colors.primarySoft, fg: theme.colors.primaryText },
    success: { bg: theme.colors.successSoft, fg: theme.colors.success },
    warning: { bg: theme.colors.warningSoft, fg: theme.colors.warning },
    danger: { bg: theme.colors.dangerSoft, fg: theme.colors.danger },
    info: { bg: theme.colors.infoSoft, fg: theme.colors.info },
  })[tone];

/** Etiqueta em pílula. O texto sempre carrega o significado (não depende só da cor). */
export const Badge = styled.span<{ $tone?: BadgeTone }>`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: ${({ theme }) => theme.radii.pill};
  font-size: ${({ theme }) => theme.fontSizes.xs};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  white-space: nowrap;
  background: ${({ theme, $tone = "neutral" }) => badgePalette(theme, $tone).bg};
  color: ${({ theme, $tone = "neutral" }) => badgePalette(theme, $tone).fg};
`;

/* ---------------------------------------------------------------- Card */

export const Card = styled.section<{ $padding?: boolean }>`
  background: ${({ theme }) => theme.colors.surface};
  border-radius: ${({ theme }) => theme.radii.lg};
  box-shadow: ${({ theme }) => theme.shadows.card};
  padding: ${({ theme, $padding = true }) => ($padding ? theme.space[6] : "0")};
  min-width: 0;
`;

export const CardTitle = styled.h2`
  font-size: ${({ theme }) => theme.fontSizes.base};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  margin-bottom: ${({ theme }) => theme.space[4]};
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${({ theme }) => theme.space[2]};
`;

/* ---------------------------------------------------------------- KPI */

interface KpiCardProps {
  icon: LucideIcon;
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  tone?: "neutral" | "danger" | "success";
}

/** Indicador: ícone em quadrado arredondado + rótulo + número em destaque (padrão das referências). */
export function KpiCard({ icon: Icon, label, value, hint, tone = "neutral" }: KpiCardProps) {
  return (
    <KpiBox>
      <KpiIcon $tone={tone} aria-hidden="true">
        <Icon size={22} strokeWidth={1.6} />
      </KpiIcon>
      <div>
        <KpiLabel>{label}</KpiLabel>
        <KpiValue>{value}</KpiValue>
        {hint && <KpiHint $tone={tone}>{hint}</KpiHint>}
      </div>
    </KpiBox>
  );
}

const KpiBox = styled(Card)`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space[4]};
  padding: ${({ theme }) => theme.space[5]};
`;

const KpiIcon = styled.div<{ $tone: string }>`
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: ${({ theme }) => theme.radii.md};
  background: ${({ theme, $tone }) =>
    $tone === "danger" ? theme.colors.dangerSoft : $tone === "success" ? theme.colors.successSoft : "#F3F1EE"};
  color: ${({ theme, $tone }) =>
    $tone === "danger" ? theme.colors.danger : $tone === "success" ? theme.colors.success : theme.colors.primaryText};
`;

const KpiLabel = styled.p`
  font-size: ${({ theme }) => theme.fontSizes.sm};
  color: ${({ theme }) => theme.colors.textMuted};
  line-height: 1.3;
`;

const KpiValue = styled.p`
  font-size: ${({ theme }) => theme.fontSizes.xl};
  font-weight: ${({ theme }) => theme.fontWeights.bold};
  line-height: 1.2;
`;

const KpiHint = styled.p<{ $tone: string }>`
  font-size: ${({ theme }) => theme.fontSizes.xs};
  color: ${({ theme, $tone }) => ($tone === "danger" ? theme.colors.danger : theme.colors.textMuted)};
`;

export const KpiGrid = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[4]};
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
`;

/* ---------------------------------------------------------------- Page header */

interface PageHeaderProps {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}

export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <Header>
      <div>
        <PageTitle>{title}</PageTitle>
        {description && <PageDescription>{description}</PageDescription>}
      </div>
      {actions && <Actions>{actions}</Actions>}
    </Header>
  );
}

const Header = styled.header`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: ${({ theme }) => theme.space[4]};
`;

export const PageTitle = styled.h1`
  font-family: ${({ theme }) => theme.fonts.display};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  font-size: ${({ theme }) => theme.fontSizes["2xl"]};
  color: ${({ theme }) => theme.colors.primaryText};
  line-height: 1.2;

  @media (min-width: ${({ theme }) => theme.breakpoints.md}px) {
    font-size: ${({ theme }) => theme.fontSizes["3xl"]};
  }
`;

const PageDescription = styled.p`
  color: ${({ theme }) => theme.colors.textMuted};
  margin-top: ${({ theme }) => theme.space[1]};
`;

const Actions = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[2]};
`;

/** Pilha vertical padrão do conteúdo de uma página. */
export const Stack = styled.div<{ $gap?: keyof DefaultTheme["space"] }>`
  display: grid;
  gap: ${({ theme, $gap = 6 }) => theme.space[$gap]};
  min-width: 0;
`;

export const Row = styled.div<{ $gap?: keyof DefaultTheme["space"]; $wrap?: boolean; $justify?: string }>`
  display: flex;
  align-items: center;
  flex-wrap: ${({ $wrap = true }) => ($wrap ? "wrap" : "nowrap")};
  justify-content: ${({ $justify = "flex-start" }) => $justify};
  gap: ${({ theme, $gap = 3 }) => theme.space[$gap]};
`;

export const Muted = styled.span`
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.sm};
`;

export const Mono = styled.span`
  font-family: ${({ theme }) => theme.fonts.mono};
  font-size: ${({ theme }) => theme.fontSizes.sm};
`;

/* ---------------------------------------------------------------- States */

interface StateProps {
  title: string;
  description?: ReactNode;
  action?: ReactNode;
}

export function EmptyState({ title, description, action }: StateProps) {
  return (
    <StateBox>
      <Inbox size={32} strokeWidth={1.4} aria-hidden="true" />
      <StateTitle>{title}</StateTitle>
      {description && <p>{description}</p>}
      {action}
    </StateBox>
  );
}

export function ErrorState({
  title = "Não foi possível carregar",
  error,
  onRetry,
}: {
  title?: string;
  error?: Error | null;
  onRetry?: () => void;
}) {
  return (
    <StateBox role="alert">
      <AlertTriangle size={32} strokeWidth={1.4} aria-hidden="true" />
      <StateTitle>{title}</StateTitle>
      {error?.message && <p>{error.message}</p>}
      {onRetry && (
        <Button variant="outline" onClick={onRetry}>
          Tentar novamente
        </Button>
      )}
    </StateBox>
  );
}

export function PermissionDenied({ description }: { description?: string }) {
  return (
    <StateBox>
      <Lock size={32} strokeWidth={1.4} aria-hidden="true" />
      <StateTitle>Acesso restrito</StateTitle>
      <p>{description ?? "Seu perfil nesta clínica não tem permissão para acessar esta área."}</p>
    </StateBox>
  );
}

const StateBox = styled.div`
  display: grid;
  justify-items: center;
  text-align: center;
  gap: ${({ theme }) => theme.space[2]};
  padding: ${({ theme }) => `${theme.space[10]} ${theme.space[4]}`};
  color: ${({ theme }) => theme.colors.textMuted};

  p {
    max-width: 48ch;
  }
`;

const StateTitle = styled.p`
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  color: ${({ theme }) => theme.colors.text};
`;

/* ---------------------------------------------------------------- Description list */

export const DetailList = styled.dl`
  margin: 0;
  display: grid;
  gap: ${({ theme }) => theme.space[3]};
  grid-template-columns: 1fr;

  @media (min-width: ${({ theme }) => theme.breakpoints.md}px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  div {
    display: grid;
    gap: 2px;
    min-width: 0;
  }
  dt {
    font-size: ${({ theme }) => theme.fontSizes.sm};
    color: ${({ theme }) => theme.colors.textMuted};
  }
  dd {
    margin: 0;
    overflow-wrap: anywhere;
  }
`;
