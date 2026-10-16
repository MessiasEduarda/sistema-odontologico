/**
 * Tokens do design system. Base visual extraída das referências (docs/design/referencias):
 * sidebar escura, primária caramelo, superfícies brancas arredondadas, campos e botões em pílula.
 * Contrastes de texto ajustados para WCAG AA. Refinamento completo na Fase 3.
 */
export const colors = {
  sidebar: "#141414",
  sidebarHover: "#232323",
  sidebarActive: "#2B2622",
  sidebarText: "#E9E4DF",
  sidebarTextMuted: "#9A938C",

  primary: "#9C7A5B",
  primaryHover: "#87684C",
  primaryActive: "#735740",
  primarySoft: "#F4EDE6",
  /** Texto sobre fundo claro na cor da marca (contraste ≥ 4,5:1). */
  primaryText: "#7A5C41",
  onPrimary: "#FFFFFF",
  tableHeaderStart: "#A88B6E",
  tableHeaderEnd: "#8C7058",

  background: "#F6F5F3",
  surface: "#FFFFFF",
  surfaceMuted: "#FAF8F6",
  border: "#E8E3DD",
  borderStrong: "#D6CEC5",

  text: "#1E1C1A",
  textMuted: "#6B655F",
  textSubtle: "#8D867F",

  success: "#2E7D4F",
  successSoft: "#E7F4EC",
  warning: "#7A5A00",
  warningSoft: "#FFF8DB",
  warningBorder: "#F0D673",
  danger: "#B3261E",
  dangerSoft: "#FCEBEA",
  info: "#2F5F8A",
  infoSoft: "#E8F0F8",

  focus: "#9C7A5B",
  overlay: "rgba(20, 20, 20, 0.45)",
} as const;

/** Títulos: Comfortaa. Textos: Metropolis. Ambas gratuitas (SIL Open Font License). */
export const fonts = {
  display: "var(--font-display), 'Comfortaa', system-ui, sans-serif",
  body: "var(--font-body), 'Metropolis', system-ui, -apple-system, 'Segoe UI', sans-serif",
  mono: "var(--font-mono), 'JetBrains Mono', ui-monospace, monospace",
} as const;

export const fontSizes = {
  xs: "0.75rem",
  sm: "0.8125rem",
  md: "0.875rem",
  base: "1rem",
  lg: "1.125rem",
  xl: "1.375rem",
  "2xl": "1.75rem",
  "3xl": "2rem",
} as const;

export const fontWeights = { regular: 400, medium: 500, semibold: 600, bold: 700 } as const;

/** Escala de 4px. */
export const space = {
  0: "0",
  1: "0.25rem",
  2: "0.5rem",
  3: "0.75rem",
  4: "1rem",
  5: "1.25rem",
  6: "1.5rem",
  8: "2rem",
  10: "2.5rem",
  12: "3rem",
} as const;

export const radii = { sm: "8px", md: "12px", lg: "16px", xl: "20px", pill: "999px" } as const;

export const shadows = {
  card: "0 1px 2px rgba(30, 28, 26, 0.04), 0 4px 16px rgba(30, 28, 26, 0.05)",
  overlay: "0 12px 40px rgba(20, 20, 20, 0.18)",
  focus: "0 0 0 3px rgba(156, 122, 91, 0.35)",
} as const;

export const breakpoints = { sm: 480, md: 768, lg: 1024, xl: 1280 } as const;

export const layout = {
  sidebarCollapsed: "64px",
  sidebarExpanded: "232px",
  contentMaxWidth: "1440px",
} as const;

export const transitions = { fast: "120ms ease", base: "200ms ease" } as const;

export const zIndex = { sidebar: 100, dropdown: 200, modal: 300, toast: 400 } as const;
