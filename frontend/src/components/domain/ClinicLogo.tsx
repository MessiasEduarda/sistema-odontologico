"use client";

import styled, { css } from "styled-components";
import { api } from "@/lib/api/endpoints";

/** Iniciais do nome da clínica (ignora palavras curtas como "da", "de"). Ex.: "Clínica Sorriso Demo" → "CSD". */
export function clinicInitials(name: string | null | undefined): string {
  const words = (name ?? "").split(/\s+/).filter((w) => w.length > 2);
  return (
    words
      .slice(0, 2)
      .map((w) => w[0]?.toUpperCase())
      .join("") || "OS"
  ).slice(0, 2);
}

/**
 * Logo da clínica sempre no mesmo lugar e tamanho: quadro de cantos arredondados na cor do menu, com a imagem (PNG)
 * ocupando o quadro inteiro. Sem logo, mostra as iniciais da clínica no mesmo quadro.
 */
export function ClinicLogo({
  name,
  logoVersion,
  size = 44,
  tone = "dark",
}: {
  name: string | null | undefined;
  logoVersion: string | null | undefined;
  size?: number;
  /** "dark": sobre o menu escuro; "light": sobre fundo claro. */
  tone?: "dark" | "light";
}) {
  return (
    <Box $size={size} $tone={tone} $image={Boolean(logoVersion)} title={name ?? undefined}>
      {logoVersion ? (
        // Imagem servida pela API com sessão; next/image não se aplica a conteúdo autenticado.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={api.clinic.logoUrl(logoVersion)} alt={`Logo da ${name ?? "clínica"}`} />
      ) : (
        <span aria-hidden="true">{clinicInitials(name)}</span>
      )}
    </Box>
  );
}

const Box = styled.div<{ $size: number; $tone: "dark" | "light"; $image: boolean }>`
  width: ${({ $size }) => $size}px;
  height: ${({ $size }) => $size}px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: ${({ $size }) => Math.round($size * 0.27)}px;
  font-family: ${({ theme }) => theme.fonts.display};
  font-weight: ${({ theme }) => theme.fontWeights.bold};
  font-size: ${({ $size }) => Math.round($size * 0.36)}px;
  letter-spacing: 0.02em;

  ${({ $image, $tone, theme }) =>
    $image
      ? css`
          background: ${theme.colors.sidebar};
          box-shadow: ${$tone === "dark" ? "0 0 0 1px rgba(255, 255, 255, 0.14)" : theme.shadows.card};
        `
      : css`
          background: linear-gradient(135deg, ${theme.colors.primary}, ${theme.colors.primaryActive});
          color: ${theme.colors.onPrimary};
        `}

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
`;
