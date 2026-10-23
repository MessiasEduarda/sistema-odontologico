"use client";

import { X } from "lucide-react";
import { useCallback, useEffect, useId, useRef, type ReactNode, type SyntheticEvent } from "react";
import styled from "styled-components";
import { AlertDialog, DISCARD_CHANGES, useFeedback } from "./Feedback";
import { IconButton } from "./IconButton";

interface ModalProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  size?: "sm" | "md" | "lg";
  confirmDiscard?: boolean;
}

export function Modal({ open, title, onClose, children, footer, size = "md", confirmDiscard = true }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const feedback = useFeedback();
  const dirty = useRef(false);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      dirty.current = false;
      dialog.showModal();
    }
    if (!open && dialog.open) dialog.close();
  }, [open]);

  const requestClose = useCallback(async () => {
    if (confirmDiscard && dirty.current) {
      const discard = await feedback.confirm(DISCARD_CHANGES);
      if (!discard) return;
    }
    onClose();
  }, [confirmDiscard, feedback, onClose]);

  const markDirty = () => {
    dirty.current = true;
  };

  const onClickCapture = (event: SyntheticEvent) => {
    const target = event.target as HTMLElement;
    if (target.closest('[role="option"]')) markDirty();
    if (confirmDiscard && dirty.current && target.closest("[data-modal-cancel]")) {
      event.preventDefault();
      event.stopPropagation();
      void requestClose();
    }
  };

  return (
    <Dialog
      ref={ref}
      aria-labelledby={titleId}
      $size={size}
      onCancel={(e) => {
        e.preventDefault();
        void requestClose();
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) void requestClose();
      }}
      onInputCapture={markDirty}
      onChangeCapture={markDirty}
      onClickCapture={onClickCapture}
    >
      {open && (
        <Inner>
          <Head>
            <Title id={titleId}>{title}</Title>
            <IconButton icon={X} label="Fechar" onClick={() => void requestClose()} />
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
  question: ReactNode;
  message?: ReactNode;
  confirmLabel?: string;
  tone?: "primary" | "danger";
  loading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export function ConfirmDialog({
  open,
  question,
  message,
  confirmLabel = "Continuar",
  tone = "primary",
  loading,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  if (!open) return null;
  return (
    <AlertDialog
      kind="attention"
      message={question}
      detail={message}
      confirmLabel={confirmLabel}
      danger={tone === "danger"}
      loading={loading}
      onConfirm={onConfirm}
      onClose={onClose}
    />
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

  @media (max-width: ${({ theme }) => theme.breakpoints.sm}px) {
    padding: ${({ theme }) => `${theme.space[4]} ${theme.space[4]}`};
  }
`;

const Title = styled.h2`
  font-family: ${({ theme }) => theme.fonts.display};
  font-size: ${({ theme }) => theme.fontSizes.lg};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
`;

const Body = styled.div`
  padding: ${({ theme }) => theme.space[6]};
  overflow-y: auto;

  @media (max-width: ${({ theme }) => theme.breakpoints.sm}px) {
    padding: ${({ theme }) => `${theme.space[5]} ${theme.space[4]}`};
  }
`;

const Foot = styled.div`
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[3]};
  padding: ${({ theme }) => `${theme.space[4]} ${theme.space[6]}`};
  border-top: 1px solid ${({ theme }) => theme.colors.border};

  @media (max-width: ${({ theme }) => theme.breakpoints.sm}px) {
    padding: ${({ theme }) => `${theme.space[3]} ${theme.space[4]}`};
    & > * {
      flex: 1 1 auto;
    }
  }
`;
