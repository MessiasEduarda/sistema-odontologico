"use client";

import { type ReactNode, useEffect, useId, useRef, useState } from "react";
import styled from "styled-components";
import { LEGAL_NOTICE, LEGAL_UPDATED_AT, PRIVACY, TERMS, type LegalSection } from "@/features/public/LegalPage";

export type LegalTab = "terms" | "privacy";

const DIALOG_WIDTH = 600;
const DIALOG_HEIGHT = 608;

interface LegalDialogProps {
  subtitle: string;
  initialTab?: LegalTab;
  footer: ReactNode;
  onClose?: () => void;
  contained?: boolean;
}

export function LegalDialog({ subtitle, initialTab = "terms", footer, onClose, contained = false }: LegalDialogProps) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const [tab, setTab] = useState<LegalTab>(initialTab);

  useEffect(() => {
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    dialogRef.current?.focus();
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
      opener?.focus();
    };
  }, []);

  useEffect(() => {
    if (!onClose) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  const sections: LegalSection[] = tab === "terms" ? TERMS : PRIVACY;

  return (
    <Overlay
      $contained={contained}
      onMouseDown={(event) => onClose && event.target === event.currentTarget && onClose()}
    >
      <Dialog
        ref={dialogRef}
        $contained={contained}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
      >
        <Header>
          <div>
            <h2 id={titleId}>Termos de Uso e Privacidade</h2>
            <p>{subtitle}</p>
          </div>
          {onClose && (
            <CloseX type="button" aria-label="Fechar" onClick={onClose}>
              <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
              </svg>
            </CloseX>
          )}
        </Header>

        <Tabs role="tablist" aria-label="Documentos">
          <TabButton type="button" role="tab" aria-selected={tab === "terms"} onClick={() => setTab("terms")}>
            Termos de Uso
          </TabButton>
          <TabButton type="button" role="tab" aria-selected={tab === "privacy"} onClick={() => setTab("privacy")}>
            Política de Privacidade
          </TabButton>
        </Tabs>

        <Body role="tabpanel" tabIndex={0}>
          <Updated>Última atualização: {LEGAL_UPDATED_AT}</Updated>
          <Notice>{LEGAL_NOTICE}</Notice>
          {sections.map((section) => (
            <section key={section.title}>
              <h3>{section.title}</h3>
              {section.paragraphs.map((paragraph) => (
                <p key={paragraph}>{paragraph}</p>
              ))}
            </section>
          ))}
        </Body>

        <Footer>{footer}</Footer>
      </Dialog>
    </Overlay>
  );
}

const Overlay = styled.div<{ $contained: boolean }>`
  position: ${({ $contained }) => ($contained ? "absolute" : "fixed")};
  inset: 0;
  z-index: ${({ theme }) => theme.zIndex.modal};
  display: grid;
  place-items: center;
  padding: ${({ theme }) => theme.space[4]};
  background: rgba(20, 20, 20, 0.55);
`;

const Dialog = styled.div<{ $contained: boolean }>`
  width: 100%;
  max-width: ${DIALOG_WIDTH}px;
  height: ${({ $contained }) => ($contained ? `min(${DIALOG_HEIGHT}px, 100%)` : `min(${DIALOG_HEIGHT}px, calc(100vh - 2rem))`)};
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 10px;
  background: ${({ theme }) => theme.colors.surface};
  box-shadow:
    0 4px 12px rgba(20, 20, 20, 0.15),
    0 30px 80px rgba(20, 20, 20, 0.35);

  &:focus {
    outline: none;
  }
`;

const Header = styled.header`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: ${({ theme }) => theme.space[4]};
  padding: ${({ theme }) => `${theme.space[6]} ${theme.space[6]} ${theme.space[5]}`};
  background: ${({ theme }) => theme.colors.primaryText};
  color: ${({ theme }) => theme.colors.onPrimary};

  h2 {
    font-family: ${({ theme }) => theme.fonts.display};
    font-size: ${({ theme }) => theme.fontSizes.xl};
    font-weight: ${({ theme }) => theme.fontWeights.bold};
  }
  p {
    margin-top: ${({ theme }) => theme.space[1]};
    font-size: ${({ theme }) => theme.fontSizes.sm};
    color: ${({ theme }) => theme.colors.primarySoft};
  }
`;

const Tabs = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: ${({ theme }) => theme.colors.surfaceMuted};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
`;

const TabButton = styled.button`
  padding: ${({ theme }) => theme.space[3]};
  border: 0;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  background: transparent;
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.md};
  cursor: pointer;

  &[aria-selected="true"] {
    background: ${({ theme }) => theme.colors.surface};
    color: ${({ theme }) => theme.colors.text};
    font-weight: ${({ theme }) => theme.fontWeights.semibold};
    border-bottom-color: ${({ theme }) => theme.colors.primary};
  }
  &:focus-visible {
    outline: none;
    box-shadow: inset ${({ theme }) => theme.shadows.focus};
  }
`;

const Body = styled.div`
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: ${({ theme }) => `${theme.space[5]} ${theme.space[6]}`};
  font-size: ${({ theme }) => theme.fontSizes.md};
  line-height: 1.6;
  color: ${({ theme }) => theme.colors.text};

  h3 {
    font-size: ${({ theme }) => theme.fontSizes.base};
    font-weight: ${({ theme }) => theme.fontWeights.semibold};
    color: ${({ theme }) => theme.colors.primaryText};
    margin: ${({ theme }) => `${theme.space[4]} 0 ${theme.space[1]}`};
  }
  p {
    margin-bottom: ${({ theme }) => theme.space[2]};
  }
  &:focus-visible {
    outline: none;
    box-shadow: inset ${({ theme }) => theme.shadows.focus};
  }
`;

const Updated = styled.p`
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.sm};
`;

const Notice = styled.p`
  padding: ${({ theme }) => theme.space[3]};
  border-radius: ${({ theme }) => theme.radii.md};
  background: ${({ theme }) => theme.colors.primarySoft};
  color: ${({ theme }) => theme.colors.textMuted};
  font-size: ${({ theme }) => theme.fontSizes.sm};
`;

const Footer = styled.footer`
  display: grid;
  gap: ${({ theme }) => theme.space[3]};
  padding: ${({ theme }) => `${theme.space[4]} ${theme.space[6]} ${theme.space[6]}`};
  border-top: 1px solid ${({ theme }) => theme.colors.border};
`;

export const DialogButton = styled.button`
  min-height: 44px;
  border: 0;
  border-radius: 8px;
  background: ${({ theme }) => theme.colors.primary};
  color: ${({ theme }) => theme.colors.onPrimary};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  font-size: ${({ theme }) => theme.fontSizes.md};
  cursor: pointer;
  transition: background ${({ theme }) => theme.transitions.fast};

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.primaryHover};
  }
  &:disabled {
    background: ${({ theme }) => theme.colors.border};
    color: ${({ theme }) => theme.colors.textSubtle};
    cursor: not-allowed;
  }
  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
`;

const CloseX = styled.button`
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  color: ${({ theme }) => theme.colors.onPrimary};
  cursor: pointer;

  &:hover {
    background: rgba(255, 255, 255, 0.24);
  }
  &:focus-visible {
    outline: none;
    box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.6);
  }
`;
