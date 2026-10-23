"use client";

import { useState, type ReactNode } from "react";
import styled from "styled-components";
import { ToothMark } from "@/components/domain/ToothMark";
import { DialogButton, LegalDialog, type LegalTab } from "@/features/auth/LegalDialog";
import { media } from "@/styles/theme";

export function PublicLayout({
  clinicName,
  title,
  actions,
  children,
}: {
  clinicName?: string;
  title: string;
  actions?: ReactNode;
  children: ReactNode;
}) {
  const [legal, setLegal] = useState<LegalTab | null>(null);
  return (
    <Page>
      <Header>
        <Brand>
          <ToothMark size={34} />
          <span>{clinicName ?? "Odonto SaaS"}</span>
        </Brand>
        {actions && <HeaderActions>{actions}</HeaderActions>}
      </Header>
      <Main>
        <Title>{title}</Title>
        {children}
      </Main>
      <Footer>
        <span>Este link é pessoal. Não o compartilhe.</span>
        <span aria-hidden="true">·</span>
        <button type="button" onClick={() => setLegal("terms")}>
          Termos de uso
        </button>
        <span aria-hidden="true">·</span>
        <button type="button" onClick={() => setLegal("privacy")}>
          Privacidade
        </button>
      </Footer>
      {legal && (
        <LegalDialog
          initialTab={legal}
          subtitle="Conheça as regras de uso e como tratamos os dados"
          onClose={() => setLegal(null)}
          footer={
            <DialogButton type="button" onClick={() => setLegal(null)}>
              Fechar
            </DialogButton>
          }
        />
      )}
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[3]};
  padding: ${({ theme }) => `${theme.space[4]} ${theme.space[4]}`};

  ${media.md} {
    padding: ${({ theme }) => `${theme.space[5]} ${theme.space[8]}`};
  }
`;

const HeaderActions = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[2]};

  button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 12px;
    border: 1px solid rgba(255, 255, 255, 0.25);
    border-radius: ${({ theme }) => theme.radii.pill};
    background: transparent;
    color: ${({ theme }) => theme.colors.sidebarText};
    font-size: ${({ theme }) => theme.fontSizes.sm};
    font-weight: ${({ theme }) => theme.fontWeights.semibold};
    cursor: pointer;
  }
  button:hover {
    background: ${({ theme }) => theme.colors.sidebarHover};
  }
  button:focus-visible {
    outline: 2px solid ${({ theme }) => theme.colors.sidebarText};
    outline-offset: 2px;
  }
`;

const Brand = styled.p`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space[3]};
  font-family: ${({ theme }) => theme.fonts.display};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  font-size: ${({ theme }) => theme.fontSizes.lg};
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
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: ${({ theme }) => theme.space[2]};
  padding: ${({ theme }) => theme.space[6]};
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.sm};

  button {
    padding: 0;
    border: 0;
    background: none;
    color: inherit;
    font-size: inherit;
    cursor: pointer;
  }
  button:hover {
    color: ${({ theme }) => theme.colors.primaryText};
  }
  button:focus-visible {
    outline: 2px solid ${({ theme }) => theme.colors.primary};
    outline-offset: 3px;
    border-radius: 2px;
  }
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
