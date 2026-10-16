"use client";

import type { ReactNode } from "react";
import styled from "styled-components";

interface PrintSheetProps {
  clinicName: string;
  clinicInfo?: string;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
}

/**
 * Folha imprimível (orçamento, receita, atestado, termo). Use com window.print(): a regra de impressão global mostra
 * apenas o conteúdo marcado com a classe "print-area". Também serve para salvar em PDF pelo navegador.
 */
export function PrintSheet({ clinicName, clinicInfo, title, children, footer }: PrintSheetProps) {
  return (
    <Sheet className="print-area">
      <Head>
        <strong>{clinicName}</strong>
        {clinicInfo && <span>{clinicInfo}</span>}
      </Head>
      <Title>{title}</Title>
      <Body>{children}</Body>
      {footer && <Foot>{footer}</Foot>}
    </Sheet>
  );
}

const Sheet = styled.article`
  background: #fff;
  color: #111;
  padding: ${({ theme }) => theme.space[8]};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radii.md};
  font-family: ${({ theme }) => theme.fonts.body};
  line-height: 1.6;

  @media print {
    border: none;
    padding: 0;
  }
`;

const Head = styled.header`
  display: grid;
  gap: 2px;
  padding-bottom: ${({ theme }) => theme.space[3]};
  border-bottom: 2px solid ${({ theme }) => theme.colors.primary};
  margin-bottom: ${({ theme }) => theme.space[5]};

  strong {
    font-family: ${({ theme }) => theme.fonts.display};
    font-size: ${({ theme }) => theme.fontSizes.lg};
    color: ${({ theme }) => theme.colors.primaryText};
  }
  span {
    font-size: ${({ theme }) => theme.fontSizes.sm};
    color: #555;
  }
`;

const Title = styled.h2`
  text-align: center;
  font-size: ${({ theme }) => theme.fontSizes.lg};
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: ${({ theme }) => theme.space[5]};
`;

const Body = styled.div`
  white-space: pre-wrap;
`;

const Foot = styled.footer`
  margin-top: ${({ theme }) => theme.space[10]};
  font-size: ${({ theme }) => theme.fontSizes.sm};
`;

/** Linha para assinatura impressa. */
export const SignatureLine = styled.div`
  margin: ${({ theme }) => theme.space[12]} auto 0;
  width: min(360px, 100%);
  border-top: 1px solid #333;
  padding-top: ${({ theme }) => theme.space[1]};
  text-align: center;
  font-size: ${({ theme }) => theme.fontSizes.sm};
`;
