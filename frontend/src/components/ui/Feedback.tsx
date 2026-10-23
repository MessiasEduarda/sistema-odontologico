"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import styled, { css } from "styled-components";

type Kind = "attention" | "success" | "error";

interface ConfirmOptions {
  message: ReactNode;
  detail?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
}

interface Request extends ConfirmOptions {
  kind: Kind;
  resolve?: (confirmed: boolean) => void;
}

export interface FeedbackApi {
  confirm: (options: ConfirmOptions) => Promise<boolean>;
  success: (message: ReactNode, detail?: ReactNode) => void;
  error: (message: ReactNode, detail?: ReactNode) => void;
}

export const DISCARD_CHANGES: ConfirmOptions = {
  message: "Caso voltar, você irá perder todos os dados preenchidos.",
  detail: "Tem certeza que deseja continuar?",
  danger: true,
};

const FeedbackContext = createContext<FeedbackApi | null>(null);

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [request, setRequest] = useState<Request | null>(null);

  const show = useCallback((next: Request) => {
    setRequest((current) => {
      current?.resolve?.(false);
      return next;
    });
  }, []);

  const api = useMemo<FeedbackApi>(
    () => ({
      confirm: (options) => new Promise<boolean>((resolve) => show({ ...options, kind: "attention", resolve })),
      success: (message, detail) => show({ kind: "success", message, detail }),
      error: (message, detail) => show({ kind: "error", message, detail }),
    }),
    [show],
  );

  const finish = (confirmed: boolean) => {
    request?.resolve?.(confirmed);
    setRequest(null);
  };

  return (
    <FeedbackContext.Provider value={api}>
      {children}
      {request && <FeedbackDialog request={request} onFinish={finish} />}
    </FeedbackContext.Provider>
  );
}

export function useFeedback(): FeedbackApi {
  const api = useContext(FeedbackContext);
  if (!api) throw new Error("useFeedback precisa estar dentro de <FeedbackProvider>.");
  return api;
}

const TITLES: Record<Kind, string> = { attention: "Atenção!", success: "Sucesso!", error: "Erro!" };

function FeedbackDialog({ request, onFinish }: { request: Request; onFinish: (confirmed: boolean) => void }) {
  return (
    <AlertDialog
      kind={request.kind}
      message={request.message}
      detail={request.detail}
      confirmLabel={request.confirmLabel}
      cancelLabel={request.cancelLabel}
      danger={request.danger}
      onConfirm={() => onFinish(true)}
      onClose={() => onFinish(false)}
    />
  );
}

export interface AlertDialogProps extends ConfirmOptions {
  kind: Kind;
  onConfirm?: () => void;
  onClose: () => void;
  loading?: boolean;
}

export function AlertDialog({
  kind,
  message,
  detail,
  confirmLabel,
  cancelLabel,
  danger,
  loading,
  onConfirm,
  onClose,
}: AlertDialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const messageId = useId();
  const isConfirm = kind === "attention";

  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
    return () => dialog?.close();
  }, []);

  return (
    <Dialog
      ref={ref}
      role={kind === "success" ? "dialog" : "alertdialog"}
      aria-labelledby={titleId}
      aria-describedby={messageId}
      onCancel={(event) => {
        event.preventDefault();
        if (!loading) onClose();
      }}
    >
      <Head $kind={kind}>
        <h2 id={titleId}>{TITLES[kind]}</h2>
      </Head>
      <Body id={messageId}>
        <Message>{message}</Message>
        {detail && <Detail>{detail}</Detail>}
      </Body>
      <Actions $columns={isConfirm ? 2 : 1}>
        {isConfirm ? (
          <>
            <ActionButton type="button" $tone="neutral" disabled={loading} onClick={onClose}>
              {cancelLabel ?? "Voltar"}
            </ActionButton>
            <ActionButton
              type="button"
              $tone={danger ? "danger" : "brand"}
              disabled={loading}
              aria-busy={loading || undefined}
              autoFocus
              onClick={onConfirm}
            >
              {loading ? "Aguarde…" : (confirmLabel ?? "Continuar")}
            </ActionButton>
          </>
        ) : (
          <ActionButton type="button" $tone={kind === "success" ? "success" : "danger"} autoFocus onClick={onClose}>
            Voltar
          </ActionButton>
        )}
      </Actions>
    </Dialog>
  );
}

const Dialog = styled.dialog`
  width: min(400px, calc(100vw - 32px));
  padding: 0;
  border: none;
  border-radius: 10px;
  overflow: hidden;
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.text};
  box-shadow:
    0 4px 12px rgba(20, 20, 20, 0.15),
    0 24px 60px rgba(20, 20, 20, 0.3);

  &::backdrop {
    background: ${({ theme }) => theme.colors.overlay};
  }
`;

const Head = styled.header<{ $kind: Kind }>`
  padding: ${({ theme }) => `${theme.space[3]} ${theme.space[5]}`};
  background: ${({ theme, $kind }) =>
    $kind === "success" ? theme.colors.success : $kind === "error" ? theme.colors.danger : theme.colors.primaryText};
  color: ${({ theme }) => theme.colors.onPrimary};

  h2 {
    font-family: ${({ theme }) => theme.fonts.display};
    font-size: ${({ theme }) => theme.fontSizes.lg};
    font-weight: ${({ theme }) => theme.fontWeights.bold};
  }
`;

const Body = styled.div`
  min-height: 96px;
  padding: ${({ theme }) => `${theme.space[4]} ${theme.space[5]}`};
`;

const Message = styled.div`
  font-size: ${({ theme }) => theme.fontSizes.base};
  color: ${({ theme }) => theme.colors.text};
`;

const Detail = styled.div`
  margin-top: ${({ theme }) => theme.space[1]};
  font-size: ${({ theme }) => theme.fontSizes.md};
  color: ${({ theme }) => theme.colors.textMuted};
`;

const Actions = styled.div<{ $columns: number }>`
  display: grid;
  grid-template-columns: repeat(${({ $columns }) => $columns}, 1fr);
  border-top: 1px solid ${({ theme }) => theme.colors.borderStrong};

  & > * + * {
    border-left: 1px solid ${({ theme }) => theme.colors.borderStrong};
  }
`;

const tones = {
  neutral: css`
    color: ${({ theme }) => theme.colors.textMuted};
  `,
  brand: css`
    color: ${({ theme }) => theme.colors.primaryText};
  `,
  success: css`
    color: ${({ theme }) => theme.colors.success};
  `,
  danger: css`
    color: ${({ theme }) => theme.colors.danger};
  `,
};

const ActionButton = styled.button<{ $tone: keyof typeof tones }>`
  min-height: 48px;
  border: 0;
  background: transparent;
  font-size: ${({ theme }) => theme.fontSizes.base};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  cursor: pointer;
  ${({ $tone }) => tones[$tone]}

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.surfaceMuted};
  }
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
  &:focus-visible {
    outline: none;
    box-shadow: inset ${({ theme }) => theme.shadows.focus};
  }
`;
