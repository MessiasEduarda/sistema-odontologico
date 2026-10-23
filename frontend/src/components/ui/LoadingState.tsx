"use client";

import styled from "styled-components";

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
