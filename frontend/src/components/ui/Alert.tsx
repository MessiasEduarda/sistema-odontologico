"use client";

import type { ReactNode } from "react";
import styled, { type DefaultTheme } from "styled-components";

type Tone = "info" | "success" | "warning" | "danger";

interface AlertProps {
  tone?: Tone;
  title?: string;
  children: ReactNode;
}

const ICONS: Record<Tone, string> = { info: "ℹ", success: "✓", warning: "⚠", danger: "!" };

/** Mensagem em destaque. Erros e avisos são anunciados imediatamente por leitores de tela. */
export function Alert({ tone = "info", title, children }: AlertProps) {
  const urgent = tone === "danger" || tone === "warning";
  return (
    <Box $tone={tone} role={urgent ? "alert" : "status"}>
      <Icon aria-hidden="true">{ICONS[tone]}</Icon>
      <div>
        {title && <Title>{title}</Title>}
        <div>{children}</div>
      </div>
    </Box>
  );
}

const palette = (theme: DefaultTheme, tone: Tone) =>
  ({
    info: { bg: theme.colors.infoSoft, border: theme.colors.info, text: theme.colors.info },
    success: { bg: theme.colors.successSoft, border: theme.colors.success, text: theme.colors.success },
    warning: { bg: theme.colors.warningSoft, border: theme.colors.warningBorder, text: theme.colors.warning },
    danger: { bg: theme.colors.dangerSoft, border: theme.colors.danger, text: theme.colors.danger },
  })[tone];

const Box = styled.div<{ $tone: Tone }>`
  display: flex;
  gap: ${({ theme }) => theme.space[3]};
  align-items: flex-start;
  padding: ${({ theme }) => `${theme.space[3]} ${theme.space[4]}`};
  border-radius: ${({ theme }) => theme.radii.md};
  border: 1px solid ${({ theme, $tone }) => palette(theme, $tone).border};
  background: ${({ theme, $tone }) => palette(theme, $tone).bg};
  color: ${({ theme, $tone }) => palette(theme, $tone).text};
  font-size: ${({ theme }) => theme.fontSizes.md};
`;

const Icon = styled.span`
  font-weight: ${({ theme }) => theme.fontWeights.bold};
  line-height: 1.5;
`;

const Title = styled.p`
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
`;
