"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import styled from "styled-components";
import { media } from "@/styles/theme";

/** Moldura das páginas abertas pelo paciente a partir de links (sem menu, sem sessão). */
export function PublicLayout({
  clinicName,
  title,
  children,
}: {
  clinicName?: string;
  title: string;
  children: ReactNode;
}) {
  return (
    <Page>
      <Header>
        <Brand>{clinicName ?? "Odonto SaaS"}</Brand>
      </Header>
      <Main>
        <Title>{title}</Title>
        {children}
      </Main>
      <Footer>
        Este link é pessoal. Não o compartilhe. · <Link href="/privacidade">Privacidade</Link>
      </Footer>
    </Page>
  );
}

const Page = styled.div`
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: ${({ theme }) => theme.colors.background};
`;

const Header = styled.header`
  background: ${({ theme }) => theme.colors.sidebar};
  color: ${({ theme }) => theme.colors.sidebarText};
  padding: ${({ theme }) => `${theme.space[4]} ${theme.space[4]}`};

  ${media.md} {
    padding: ${({ theme }) => `${theme.space[5]} ${theme.space[8]}`};
  }
`;

const Brand = styled.p`
  font-family: ${({ theme }) => theme.fonts.display};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  font-size: ${({ theme }) => theme.fontSizes.xl};
`;

const Main = styled.main`
  flex: 1;
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
  padding: ${({ theme }) => `${theme.space[6]} ${theme.space[4]}`};
  display: grid;
  align-content: start;
  gap: ${({ theme }) => theme.space[5]};
`;

const Title = styled.h1`
  font-family: ${({ theme }) => theme.fonts.display};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  font-size: ${({ theme }) => theme.fontSizes["2xl"]};
  color: ${({ theme }) => theme.colors.primaryText};
`;

const Footer = styled.footer`
  text-align: center;
  padding: ${({ theme }) => theme.space[6]};
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.sm};
`;

export const ConsentText = styled.div`
  white-space: pre-wrap;
  line-height: 1.6;
  max-height: 420px;
  overflow: auto;
  padding: ${({ theme }) => theme.space[4]};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radii.md};
  background: ${({ theme }) => theme.colors.surface};
`;
