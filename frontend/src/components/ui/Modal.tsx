"use client";

import { X } from "lucide-react";
import { useEffect, useId, useRef, type ReactNode } from "react";
import styled from "styled-components";
import { Button } from "./Button";
import { IconButton } from "./IconButton";

interface ModalProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  size?: "sm" | "md" | "lg";
}

/**
 * Janela modal sobre o elemento nativo <dialog>: o navegador cuida do foco preso, da tecla Esc e da camada acima da
 * página (sem sobreposição incorreta de elementos). Fecha também ao clicar fora.
 */
export function Modal({ open, title, onClose, children, footer, size = "md" }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <Dialog
      ref={ref}
      aria-labelledby={titleId}
      $size={size}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      {open && (
        <Inner>
          <Head>
            <Title id={titleId}>{title}</Title>
            <IconButton icon={X} label="Fechar" onClick={onClose} />
          </Head>
          <Body>{children}</Body>
          {footer && <Foot>{footer}</Foot>}
        </Inner>
      )}
    </Dialog>
  );
}

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: ReactNode;
  confirmLabel?: string;
  tone?: "primary" | "danger";
  loading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = "Confirmar",
  tone = "primary",
  loading,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      size="sm"
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={loading}>
            Cancelar
          </Button>
          <Button variant={tone === "danger" ? "danger" : "primary"} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      {message}
    </Modal>
  );
}

const widths = { sm: "440px", md: "640px", lg: "900px" };

const Dialog = styled.dialog<{ $size: keyof typeof widths }>`
  width: min(${({ $size }) => widths[$size]}, calc(100vw - 32px));
  max-height: calc(100vh - 48px);
  padding: 0;
  border: none;
  border-radius: ${({ theme }) => theme.radii.lg};
  box-shadow: ${({ theme }) => theme.shadows.overlay};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.text};

  &::backdrop {
    background: ${({ theme }) => theme.colors.overlay};
    backdrop-filter: blur(2px);
  }
`;

const Inner = styled.div`
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 48px);
`;

const Head = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${({ theme }) => theme.space[3]};
  padding: ${({ theme }) => `${theme.space[5]} ${theme.space[6]}`};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
`;

const Title = styled.h2`
  font-family: ${({ theme }) => theme.fonts.display};
  font-size: ${({ theme }) => theme.fontSizes.lg};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
`;

const Body = styled.div`
  padding: ${({ theme }) => theme.space[6]};
  overflow-y: auto;
`;

const Foot = styled.div`
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[3]};
  padding: ${({ theme }) => `${theme.space[4]} ${theme.space[6]}`};
  border-top: 1px solid ${({ theme }) => theme.colors.border};
`;
