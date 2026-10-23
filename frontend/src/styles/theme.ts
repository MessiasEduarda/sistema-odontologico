import {
  breakpoints,
  colors,
  fontSizes,
  fontWeights,
  fonts,
  layout,
  radii,
  shadows,
  space,
  transitions,
  zIndex,
} from "./tokens";

export const theme = {
  colors,
  fonts,
  fontSizes,
  fontWeights,
  space,
  radii,
  shadows,
  breakpoints,
  layout,
  transitions,
  zIndex,
} as const;

export type AppTheme = typeof theme;

export const media = {
  sm: `@media (min-width: ${breakpoints.sm}px)`,
  md: `@media (min-width: ${breakpoints.md}px)`,
  lg: `@media (min-width: ${breakpoints.lg}px)`,
  xl: `@media (min-width: ${breakpoints.xl}px)`,
} as const;
