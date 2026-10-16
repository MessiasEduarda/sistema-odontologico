import type { Metadata } from "next";
import { Comfortaa, JetBrains_Mono } from "next/font/google";
import localFont from "next/font/local";
import type { ReactNode } from "react";
import { Providers } from "./providers";

// Fontes gratuitas (SIL Open Font License), servidas pelo próprio app via next/font.
// Títulos: Comfortaa (geométrica arredondada). Textos: Metropolis (arquivos em src/fonts/metropolis).
const display = Comfortaa({ subsets: ["latin"], weight: ["500", "600", "700"], variable: "--font-display" });
const body = localFont({
  src: [
    { path: "../fonts/metropolis/Metropolis-Regular.otf", weight: "400" },
    { path: "../fonts/metropolis/Metropolis-SemiBold.otf", weight: "600" },
    { path: "../fonts/metropolis/Metropolis-Bold.otf", weight: "700" },
  ],
  variable: "--font-body",
  display: "swap",
  // A Metropolis reserva mais espaço abaixo da linha de base (205) que acima das maiúsculas (108), o que deixa o
  // texto "alto" em botões, campos e etiquetas. Redistribuir a altura (mesmo total de 1em) centraliza as maiúsculas.
  declarations: [
    { prop: "ascent-override", value: "84.35%" },
    { prop: "descent-override", value: "15.65%" },
    { prop: "line-gap-override", value: "0%" },
  ],
});
const mono = JetBrains_Mono({ subsets: ["latin"], weight: ["400", "500"], variable: "--font-mono" });

export const metadata: Metadata = {
  title: { default: "Odonto SaaS", template: "%s · Odonto SaaS" },
  description: "Gestão para clínicas odontológicas",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="pt-BR" className={`${display.variable} ${body.variable} ${mono.variable}`}>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
