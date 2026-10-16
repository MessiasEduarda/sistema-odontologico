"use client";

import Image from "next/image";
import { type ReactNode, useCallback, useState } from "react";
import styled from "styled-components";
import brandLogo from "@/assets/odonto-saas-logo.png";
import { TOOTH_PATH } from "@/components/domain/ToothMark";
import { media } from "@/styles/theme";
import { DialogButton, LegalDialog, type LegalTab } from "./LegalDialog";

interface AuthLayoutProps {
  title: string;
  /** Esconde o título (continua para leitores de tela) e usa a logo grande no lugar dele. */
  hideTitle?: boolean;
  subtitle?: ReactNode;
  children: ReactNode;
}

const TOOTH = TOOTH_PATH;

/** Paleta da tela de acesso: só cores chapadas (sem degradê). */
const P = {
  page: "#F4EDE6",
  blob: "#9C7A5B",
  panel: "#7A5C41",
  dark: "#141414",
  darkHover: "#2B2622",
  white: "#FFFFFF",
  soft: "#F4EDE6",
  line: "#D6CEC5",
  skin: "#E8C4A8",
  shirt: "#C9A98A",
  leaf: "#C9B29B",
  gray: "#8D867F",
};

/**
 * Telas de acesso: cartão em primeiro plano (formulário à esquerda, ilustração à direita) sobre um fundo de cor
 * única; a profundidade vem da sombra nas bordas do cartão. No celular, só o formulário.
 */
export function AuthLayout({ title, hideTitle = false, subtitle, children }: AuthLayoutProps) {
  const [legal, setLegal] = useState<LegalTab | null>(null);
  const closeLegal = useCallback(() => setLegal(null), []);
  return (
    <Page>
      <Main>
        <Stage>
          <Card>
            <FormSide>
              <Image
                src={brandLogo}
                alt="Odonto SaaS"
                priority
                style={{ width: hideTitle ? 210 : 110, height: "auto", marginBottom: hideTitle ? 28 : 0 }}
              />
              <Title className={hideTitle ? "sr-only" : undefined}>{title}</Title>
              {subtitle && <Subtitle>{subtitle}</Subtitle>}
              <Content>{children}</Content>
              <Legal>
                <button type="button" onClick={() => setLegal("terms")}>
                  Termos de uso
                </button>
                <span aria-hidden="true">·</span>
                <button type="button" onClick={() => setLegal("privacy")}>
                  Privacidade
                </button>
              </Legal>
            </FormSide>
            <ArtSide aria-hidden="true">
              <DentalIllustration />
            </ArtSide>
            {legal && (
              <LegalDialog
                contained
                initialTab={legal}
                subtitle="Conheça as regras de uso e como tratamos os dados"
                onClose={closeLegal}
                footer={
                  <DialogButton type="button" onClick={closeLegal}>
                    Fechar
                  </DialogButton>
                }
              />
            )}
          </Card>
        </Stage>
      </Main>
    </Page>
  );
}

/** Ilustração própria (SVG, cores chapadas): dentista apontando para um celular com dente na tela e paciente sentado. */
function DentalIllustration() {
  const dots = (y: number, n: number) =>
    Array.from({ length: n }, (_, i) => <circle key={`${y}-${i}`} cx={192 + i * 9} cy={y} r="2.4" fill={P.panel} />);
  return (
    <svg viewBox="72 8 368 318" width="100%" style={{ maxWidth: 460 }}>
      {/* chão */}
      <ellipse cx="230" cy="318" rx="180" ry="8" fill={P.soft} />

      {/* caminho pontilhado com dente */}
      <path d="M150 40 C 240 0, 340 14, 352 74" fill="none" stroke={P.blob} strokeWidth="2" strokeDasharray="4 6" />
      <g transform="translate(128 24) scale(0.16)">
        <path d={TOOTH} fill={P.blob} />
      </g>

      {/* brilhos */}
      {[
        [362, 104, 1],
        [384, 132, 0.6],
      ].map(([x, y, s]) => (
        <path
          key={`${x}-${y}`}
          transform={`translate(${x} ${y}) scale(${s})`}
          d="M0 -12 C 2 -3, 3 -2, 12 0 C 3 2, 2 3, 0 12 C -2 3, -3 2, -12 0 C -3 -2, -2 -3, 0 -12 Z"
          fill={P.leaf}
        />
      ))}

      {/* celular: moldura atrás e tela na frente */}
      <rect x="190" y="70" width="150" height="248" rx="18" fill={P.dark} />
      <rect x="160" y="52" width="142" height="230" rx="16" fill={P.blob} />
      <circle cx="231" cy="108" r="31" fill={P.soft} stroke={P.white} strokeWidth="5" />
      <g transform="translate(208.6 83) scale(0.28)">
        <path d={TOOTH} fill={P.panel} />
      </g>
      <rect x="182" y="156" width="98" height="13" rx="6.5" fill={P.white} />
      {dots(162.5, 6)}
      <rect x="182" y="178" width="98" height="13" rx="6.5" fill={P.white} />
      {dots(184.5, 9)}
      <rect x="182" y="204" width="98" height="14" rx="7" fill={P.dark} />

      {/* dentista em pé */}
      <rect x="97" y="88" width="30" height="46" rx="12" fill={P.dark} />
      <circle cx="115" cy="97" r="11" fill={P.skin} />
      <path d="M103 94 a12 12 0 0 1 24 -2 l-6 -2 l-16 4 Z" fill={P.dark} />
      <rect x="111" y="106" width="8" height="8" fill={P.skin} />
      <path d="M97 114 h36 l6 72 h-48 Z" fill={P.white} stroke={P.line} strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M109 114 l6 15 l6 -15 Z" fill={P.blob} />
      <line x1="131" y1="121" x2="161" y2="105" stroke={P.line} strokeWidth="11" strokeLinecap="round" />
      <line x1="131" y1="121" x2="161" y2="105" stroke={P.white} strokeWidth="8" strokeLinecap="round" />
      <circle cx="164" cy="104" r="5" fill={P.skin} />
      <line x1="100" y1="122" x2="96" y2="170" stroke={P.line} strokeWidth="11" strokeLinecap="round" />
      <line x1="100" y1="122" x2="96" y2="170" stroke={P.white} strokeWidth="8" strokeLinecap="round" />
      <circle cx="96" cy="174" r="5" fill={P.skin} />
      <rect x="104" y="185" width="11" height="110" rx="3" fill={P.dark} />
      <rect x="119" y="185" width="11" height="110" rx="3" fill={P.dark} />
      <ellipse cx="109" cy="297" rx="10" ry="4.5" fill={P.darkHover} />
      <ellipse cx="126" cy="297" rx="10" ry="4.5" fill={P.darkHover} />

      {/* paciente sentado com notebook */}
      <g transform="translate(332 304) scale(1.4) translate(-332 -304)">
        <ellipse cx="332" cy="300" rx="38" ry="11" fill={P.dark} />
        <path d="M314 244 h36 l5 52 h-46 Z" fill={P.shirt} />
        <circle cx="332" cy="230" r="11" fill={P.skin} />
        <path d="M321 229 A11 11 0 0 1 343 229 Z" fill={P.dark} />
        <path d="M314 286 l5 -18 h32 l-5 18 Z" fill={P.line} />
        <rect x="308" y="285" width="46" height="4" rx="2" fill={P.gray} />
        <line x1="316" y1="254" x2="320" y2="282" stroke={P.shirt} strokeWidth="7" strokeLinecap="round" />
        <line x1="348" y1="254" x2="344" y2="282" stroke={P.shirt} strokeWidth="7" strokeLinecap="round" />
      </g>

      {/* planta */}
      <g transform="translate(26 0)">
        <ellipse cx="376" cy="252" rx="6" ry="20" transform="rotate(-24 376 252)" fill={P.leaf} />
        <ellipse cx="398" cy="250" rx="6" ry="20" transform="rotate(24 398 250)" fill={P.leaf} />
        <ellipse cx="387" cy="242" rx="6" ry="22" fill={P.leaf} />
        <rect x="374" y="270" width="26" height="30" rx="4" fill={P.line} />
      </g>
    </svg>
  );
}

const Page = styled.div`
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: ${P.page};
`;

const Main = styled.main`
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  align-content: center;
  gap: ${({ theme }) => theme.space[5]};
  padding: ${({ theme }) => `${theme.space[10]} ${theme.space[4]}`};
`;

const Stage = styled.div`
  position: relative;
  width: 100%;
  max-width: 440px;

  ${media.lg} {
    max-width: 1080px;
  }
`;

const Card = styled.section`
  position: relative;
  display: grid;
  border-radius: 8px;
  overflow: hidden;
  background: ${P.white};
  /* sombra em todas as bordas: o cartão parece flutuar à frente do fundo */
  box-shadow:
    0 0 0 1px rgba(20, 20, 20, 0.04),
    0 4px 10px rgba(20, 20, 20, 0.12),
    0 24px 48px rgba(20, 20, 20, 0.2),
    0 48px 110px rgba(20, 20, 20, 0.28);

  ${media.lg} {
    grid-template-columns: 1fr 1fr;
    min-height: 640px;
  }
`;

/** Lado do formulário: caramelo chapado; campos brancos em pílula e botão escuro, como no modelo de referência. */
const FormSide = styled.div`
  display: grid;
  align-content: center;
  justify-items: center;
  gap: ${({ theme }) => theme.space[4]};
  padding: ${({ theme }) => `${theme.space[10]} ${theme.space[6]}`};
  background: ${P.panel};
  color: ${P.white};
  text-align: center;

  ${media.sm} {
    padding: ${({ theme }) => `${theme.space[12]} ${theme.space[10]}`};
  }
`;

const Content = styled.div`
  width: 100%;
  max-width: 400px;
  display: grid;
  gap: ${({ theme }) => theme.space[4]};
  text-align: left;

  label {
    color: ${P.white};
  }
  /* Altura fixa com texto centralizado; borda fina (1px), sem anel grosso no foco. */
  input {
    height: 48px;
    min-height: 48px;
    padding: 0 ${({ theme }) => theme.space[5]};
    line-height: normal;
    color: ${({ theme }) => theme.colors.text};
    border: 1px solid transparent;
  }
  /* Mesmo cinza claro em todos os campos (inclusive quando o navegador marca o campo como preenchimento automático). */
  input::placeholder,
  input:-webkit-autofill::placeholder {
    color: #bdb6af;
    -webkit-text-fill-color: #bdb6af;
    opacity: 1;
  }
  input:hover:not(:disabled, :focus, [aria-invalid="true"]) {
    border-color: transparent;
  }
  input:focus,
  input[aria-invalid="true"] {
    border: 1px solid ${P.dark};
    box-shadow: none;
  }
  /* Preenchimento automático do navegador: mantém o campo branco (vem depois para vencer o foco/erro). */
  input:-webkit-autofill,
  input:-webkit-autofill:hover,
  input:-webkit-autofill:focus {
    -webkit-text-fill-color: ${({ theme }) => theme.colors.text};
    box-shadow: 0 0 0 40px ${P.white} inset;
  }
  /* dicas dos campos sobre o caramelo */
  input + p {
    color: ${P.soft};
  }
  p[role="alert"] {
    color: #ffd9d2;
    font-weight: ${({ theme }) => theme.fontWeights.semibold};
  }
  button[type="submit"] {
    height: 48px;
    background: ${P.dark};
    border-color: ${P.dark};
    color: ${P.white};
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }
  button[type="submit"]:hover:not(:disabled) {
    background: ${P.darkHover};
    border-color: ${P.darkHover};
  }
  > button[type="button"] {
    color: ${P.white};
  }
  > button[type="button"]:hover:not(:disabled) {
    background: rgba(255, 255, 255, 0.12);
    color: ${P.white};
  }
  a {
    color: ${P.white};
  }
`;

/** Mesmo estilo dos títulos de página do sistema (PageTitle), em branco sobre o caramelo. */
const Title = styled.h1`
  font-family: ${({ theme }) => theme.fonts.display};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  font-size: ${({ theme }) => theme.fontSizes["2xl"]};
  line-height: 1.2;
  color: ${P.white};
  margin-top: ${({ theme }) => theme.space[2]};

  @media (min-width: ${({ theme }) => theme.breakpoints.md}px) {
    font-size: ${({ theme }) => theme.fontSizes["3xl"]};
  }
`;

const Subtitle = styled.div`
  max-width: 340px;
  color: ${P.soft};
  font-size: ${({ theme }) => theme.fontSizes.md};
  margin-top: -${({ theme }) => theme.space[2]};
`;

const ArtSide = styled.div`
  display: none;
  align-items: center;
  justify-content: center;
  padding: ${({ theme }) => theme.space[8]};
  background: ${P.white};

  ${media.lg} {
    display: flex;
  }
`;

const Legal = styled.p`
  display: flex;
  gap: ${({ theme }) => theme.space[2]};
  margin-top: ${({ theme }) => theme.space[4]};
  font-size: ${({ theme }) => theme.fontSizes.sm};
  color: ${P.soft};

  button {
    padding: 0;
    border: 0;
    background: none;
    color: inherit;
    font-size: inherit;
    text-decoration: none;
    cursor: pointer;
  }
  button:hover {
    color: ${P.white};
  }
  button:focus-visible {
    outline: 2px solid ${P.white};
    outline-offset: 3px;
    border-radius: 2px;
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
