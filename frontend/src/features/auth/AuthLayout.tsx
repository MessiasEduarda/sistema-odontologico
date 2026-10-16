"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import styled from "styled-components";
import { media } from "@/styles/theme";

interface AuthLayoutProps {
  title: string;
  subtitle?: ReactNode;
  children: ReactNode;
}

/** Contorno de dente (molar) usado na ilustração e no fundo. */
const TOOTH =
  "M80 30C64 18 40 20 32 40C24 60 30 80 38 98C44 112 44 140 52 158C58 170 70 166 72 150C74 136 76 124 80 124" +
  "C84 124 86 136 88 150C90 166 102 170 108 158C116 140 116 112 122 98C130 80 136 60 128 40C120 20 96 18 80 30Z";

/**
 * Telas de acesso: um cartão em destaque (formulário + ilustração odontológica) sobre um fundo com profundidade —
 * camadas escuras, brilhos caramelo desfocados e silhuetas de dentes em escalas diferentes. No celular, só o cartão.
 */
export function AuthLayout({ title, subtitle, children }: AuthLayoutProps) {
  return (
    <Page>
      <Depth aria-hidden="true">
        <Glow $x="12%" $y="18%" $size={520} $opacity={0.35} />
        <Glow $x="88%" $y="82%" $size={620} $opacity={0.28} />
        <Glow $x="70%" $y="10%" $size={300} $opacity={0.18} />
        <ToothShape $x="6%" $y="62%" $size={260} $rotate={-18} $opacity={0.07} $blur={2} />
        <ToothShape $x="84%" $y="8%" $size={180} $rotate={14} $opacity={0.09} $blur={1} />
        <ToothShape $x="76%" $y="68%" $size={120} $rotate={-8} $opacity={0.12} $blur={0} />
        <ToothShape $x="22%" $y="4%" $size={90} $rotate={22} $opacity={0.1} $blur={0} />
        <ToothShape $x="46%" $y="86%" $size={70} $rotate={-30} $opacity={0.08} $blur={0} />
      </Depth>

      <Main>
        <Card>
          <FormSide>
            <Brand>
              <BrandMark aria-hidden="true">
                <svg viewBox="0 0 160 180" width="22" height="22">
                  <path d={TOOTH} fill="currentColor" />
                </svg>
              </BrandMark>
              <span>Odonto SaaS</span>
            </Brand>
            <Title>{title}</Title>
            {subtitle && <Subtitle>{subtitle}</Subtitle>}
            {children}
          </FormSide>
          <ArtSide aria-hidden="true">
            <DentalIllustration />
            <ArtText>
              <strong>Sua clínica em um só lugar</strong>
              <span>Agenda, prontuário, termos e financeiro com segurança e cuidado em cada atendimento.</span>
            </ArtText>
          </ArtSide>
        </Card>
        <Legal>
          <Link href="/termos-de-uso">Termos de uso</Link>
          <span aria-hidden="true">·</span>
          <Link href="/privacidade">Privacidade</Link>
        </Legal>
      </Main>
    </Page>
  );
}

/** Ilustração própria (SVG): dente com brilho, agenda e prancheta, na paleta do sistema. */
function DentalIllustration() {
  return (
    <svg viewBox="0 0 360 300" width="100%" style={{ maxWidth: 340 }}>
      <defs>
        <linearGradient id="auth-tooth" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#FFFFFF" />
          <stop offset="1" stopColor="#F4EDE6" />
        </linearGradient>
        <filter id="auth-shadow" x="-30%" y="-30%" width="160%" height="160%">
          <feDropShadow dx="0" dy="10" stdDeviation="12" floodColor="#735740" floodOpacity="0.22" />
        </filter>
      </defs>
      <circle cx="180" cy="150" r="118" fill="#FFFFFF" opacity="0.55" />
      <circle cx="180" cy="150" r="86" fill="#FFFFFF" opacity="0.6" />
      <path
        d="M52 236 C 90 270, 150 280, 214 262"
        fill="none"
        stroke="#9C7A5B"
        strokeWidth="2"
        strokeDasharray="4 7"
        opacity="0.55"
      />

      {/* Agenda */}
      <g transform="translate(34 64)" filter="url(#auth-shadow)">
        <rect width="92" height="84" rx="14" fill="#FFFFFF" />
        <rect width="92" height="22" rx="14" fill="#9C7A5B" />
        <rect y="12" width="92" height="10" fill="#9C7A5B" />
        <circle cx="24" cy="6" r="3" fill="#F4EDE6" />
        <circle cx="68" cy="6" r="3" fill="#F4EDE6" />
        {[0, 1, 2].map((r) =>
          [0, 1, 2, 3].map((c) => (
            <rect
              key={`${r}-${c}`}
              x={12 + c * 18}
              y={32 + r * 16}
              width="12"
              height="9"
              rx="3"
              fill={r === 1 && c === 2 ? "#9C7A5B" : "#EADFD3"}
            />
          )),
        )}
      </g>

      {/* Prancheta */}
      <g transform="translate(246 150)" filter="url(#auth-shadow)">
        <rect width="80" height="100" rx="12" fill="#FFFFFF" />
        <rect x="26" y="-8" width="28" height="16" rx="6" fill="#141414" />
        <rect x="14" y="26" width="52" height="7" rx="3.5" fill="#EADFD3" />
        <rect x="14" y="42" width="40" height="7" rx="3.5" fill="#EADFD3" />
        <rect x="14" y="58" width="46" height="7" rx="3.5" fill="#EADFD3" />
        <circle cx="58" cy="82" r="11" fill="#2E7D4F" />
        <path d="M52.5 82 l4 4 l7-8" fill="none" stroke="#FFFFFF" strokeWidth="3" strokeLinecap="round" />
      </g>

      {/* Dente */}
      <g transform="translate(108 52) scale(0.9)" filter="url(#auth-shadow)">
        <path d={TOOTH} fill="url(#auth-tooth)" stroke="#9C7A5B" strokeWidth="5" strokeLinejoin="round" />
        <path d="M54 46 C 46 56, 46 70, 50 82" fill="none" stroke="#FFFFFF" strokeWidth="9" strokeLinecap="round" />
      </g>

      {/* Brilhos */}
      {[
        [252, 66, 1],
        [276, 104, 0.6],
        [96, 196, 0.7],
      ].map(([x, y, s]) => (
        <path
          key={`${x}-${y}`}
          transform={`translate(${x} ${y}) scale(${s})`}
          d="M0 -14 C 2 -4, 4 -2, 14 0 C 4 2, 2 4, 0 14 C -2 4, -4 2, -14 0 C -4 -2, -2 -4, 0 -14 Z"
          fill="#9C7A5B"
        />
      ))}
    </svg>
  );
}

const Page = styled.div`
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: radial-gradient(120% 90% at 50% 0%, #2b2622 0%, #1c1916 55%, #121110 100%);
`;

const Depth = styled.div`
  position: absolute;
  inset: 0;
  pointer-events: none;
`;

const Glow = styled.div<{ $x: string; $y: string; $size: number; $opacity: number }>`
  position: absolute;
  left: ${({ $x }) => $x};
  top: ${({ $y }) => $y};
  width: ${({ $size }) => $size}px;
  height: ${({ $size }) => $size}px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: radial-gradient(circle, #9c7a5b 0%, rgba(156, 122, 91, 0) 70%);
  opacity: ${({ $opacity }) => $opacity};
  filter: blur(20px);
`;

const ToothShape = styled.div<{
  $x: string;
  $y: string;
  $size: number;
  $rotate: number;
  $opacity: number;
  $blur: number;
}>`
  position: absolute;
  left: ${({ $x }) => $x};
  top: ${({ $y }) => $y};
  width: ${({ $size }) => $size}px;
  height: ${({ $size }) => $size * 1.125}px;
  opacity: ${({ $opacity }) => $opacity};
  filter: blur(${({ $blur }) => $blur}px);
  transform: rotate(${({ $rotate }) => $rotate}deg);
  background: #e9e4df;
  mask: url("data:image/svg+xml,${encodeURIComponent(
      `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 160 180'><path d='${TOOTH}'/></svg>`,
    )}")
    center / contain no-repeat;
`;

const Main = styled.main`
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  align-content: center;
  gap: ${({ theme }) => theme.space[4]};
  padding: ${({ theme }) => `${theme.space[8]} ${theme.space[4]}`};
`;

const Card = styled.section`
  width: 100%;
  max-width: 440px;
  display: grid;
  background: ${({ theme }) => theme.colors.surface};
  border-radius: 28px;
  overflow: hidden;
  box-shadow:
    0 2px 6px rgba(0, 0, 0, 0.25),
    0 30px 80px rgba(0, 0, 0, 0.45),
    0 0 0 1px rgba(255, 255, 255, 0.06);

  ${media.lg} {
    max-width: 960px;
    grid-template-columns: 1fr 1fr;
  }
`;

const FormSide = styled.div`
  display: grid;
  align-content: center;
  gap: ${({ theme }) => theme.space[5]};
  padding: ${({ theme }) => `${theme.space[8]} ${theme.space[6]}`};

  ${media.sm} {
    padding: ${({ theme }) => `${theme.space[12]} ${theme.space[10]}`};
  }
`;

const Brand = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space[2]};
  font-family: ${({ theme }) => theme.fonts.display};
  font-weight: ${({ theme }) => theme.fontWeights.bold};
  color: ${({ theme }) => theme.colors.primaryText};
`;

const BrandMark = styled.span`
  width: 36px;
  height: 36px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  background: ${({ theme }) => theme.colors.sidebar};
  color: ${({ theme }) => theme.colors.primarySoft};
`;

const ArtSide = styled.div`
  display: none;
  position: relative;
  align-content: center;
  justify-items: center;
  gap: ${({ theme }) => theme.space[6]};
  padding: ${({ theme }) => theme.space[10]};
  background: radial-gradient(
    90% 70% at 50% 35%,
    #fbf7f3 0%,
    ${({ theme }) => theme.colors.primarySoft} 60%,
    #eadfd3 100%
  );

  ${media.lg} {
    display: grid;
  }
`;

const ArtText = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[2]};
  text-align: center;
  max-width: 34ch;

  strong {
    font-family: ${({ theme }) => theme.fonts.display};
    font-size: ${({ theme }) => theme.fontSizes.lg};
    color: ${({ theme }) => theme.colors.primaryText};
  }
  span {
    color: ${({ theme }) => theme.colors.textMuted};
    font-size: ${({ theme }) => theme.fontSizes.md};
  }
`;

const Title = styled.h1`
  font-family: ${({ theme }) => theme.fonts.display};
  font-weight: ${({ theme }) => theme.fontWeights.bold};
  font-size: ${({ theme }) => theme.fontSizes["2xl"]};
  color: ${({ theme }) => theme.colors.text};
`;

const Subtitle = styled.div`
  color: ${({ theme }) => theme.colors.textMuted};
  margin-top: -${({ theme }) => theme.space[3]};
`;

const Legal = styled.p`
  display: flex;
  gap: ${({ theme }) => theme.space[2]};
  font-size: ${({ theme }) => theme.fontSizes.sm};
  color: ${({ theme }) => theme.colors.sidebarTextMuted};

  a {
    color: inherit;
  }
  a:hover {
    color: ${({ theme }) => theme.colors.sidebarText};
  }
`;

export const Form = styled.form`
  display: grid;
  gap: ${({ theme }) => theme.space[4]};
`;

export const FormFooter = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[2]};
  font-size: ${({ theme }) => theme.fontSizes.md};
`;
