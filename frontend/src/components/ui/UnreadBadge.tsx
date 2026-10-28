"use client";

import styled, { css } from "styled-components";

export function UnreadBadge({
  count,
  floating = false,
  announce = false,
  className,
}: {
  count: number;
  floating?: boolean;
  announce?: boolean;
  className?: string;
}) {
  if (count <= 0) return null;
  return (
    <>
      <Bubble aria-hidden="true" $floating={floating} className={className} data-unread-badge>
        <Digits>{count > 99 ? "99+" : count}</Digits>
      </Bubble>
      {announce && <SrOnly>, {unreadLabel(count)}</SrOnly>}
    </>
  );
}

export function unreadLabel(count: number) {
  return count === 1 ? "1 mensagem não lida" : `${count} mensagens não lidas`;
}

const SrOnly = styled.span`
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
`;

export const WithBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
`;

const Digits = styled.span`
  display: block;
  line-height: 1;
`;

const Bubble = styled.span<{ $floating: boolean }>`
  box-sizing: border-box;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: ${({ theme }) => theme.radii.pill};
  background: ${({ theme }) => theme.colors.danger};
  color: #fff;
  font-family: ${({ theme }) => theme.fonts.body};
  font-size: 0.6875rem;
  font-weight: ${({ theme }) => theme.fontWeights.bold};
  line-height: 1;
  font-variant-numeric: tabular-nums lining-nums;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  vertical-align: middle;
  text-align: center;

  ${({ $floating, theme }) =>
    $floating &&
    css`
      position: absolute;
      top: -7px;
      right: -9px;
      min-width: 18px;
      height: 18px;
      padding: 0 4px;
      font-size: 0.625rem;
      box-shadow: 0 0 0 2px ${theme.colors.sidebar};
    `}
`;
