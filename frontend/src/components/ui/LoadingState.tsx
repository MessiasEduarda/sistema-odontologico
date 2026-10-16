"use client";

import styled from "styled-components";

/** Estado de carregamento padrão de página/seção (anunciado a leitores de tela). */
export function LoadingState({ label = "Carregando…" }: { label?: string }) {
  return (
    <Box role="status" aria-live="polite">
      {label}
    </Box>
  );
}

const Box = styled.div`
  min-height: 50vh;
  display: grid;
  place-items: center;
  color: ${({ theme }) => theme.colors.textMuted};
`;
