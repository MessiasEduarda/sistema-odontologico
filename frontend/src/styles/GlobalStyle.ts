"use client";

import { createGlobalStyle } from "styled-components";

export const GlobalStyle = createGlobalStyle`
  *, *::before, *::after { box-sizing: border-box; }

  html { -webkit-text-size-adjust: 100%; }

  body {
    margin: 0;
    min-height: 100vh;
    background: ${({ theme }) => theme.colors.background};
    color: ${({ theme }) => theme.colors.text};
    font-family: ${({ theme }) => theme.fonts.body};
    font-size: ${({ theme }) => theme.fontSizes.md};
    line-height: 1.5;
    -webkit-font-smoothing: antialiased;
  }

  h1, h2, h3, h4, p { margin: 0; }

  /* Títulos sempre com a fonte de destaque (Comfortaa). */
  h1, h2, h3, h4 { font-family: ${({ theme }) => theme.fonts.display}; }

  button, input, select, textarea { font: inherit; color: inherit; }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }

  @media print {
    .no-print { display: none !important; }
    body { background: #fff; }
    /* Impressão de documento: só a folha marcada aparece (ver lib/print.ts). */
    body.print-mode * { visibility: hidden; }
    body.print-mode .print-area,
    body.print-mode .print-area * { visibility: visible; }
    body.print-mode .print-area { position: absolute; left: 0; top: 0; width: 100%; }
  }

  a { color: ${({ theme }) => theme.colors.primaryText}; }

  :focus-visible {
    outline: 2px solid ${({ theme }) => theme.colors.focus};
    outline-offset: 2px;
  }

  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after {
      animation-duration: 0.01ms !important;
      transition-duration: 0.01ms !important;
    }
  }
`;
