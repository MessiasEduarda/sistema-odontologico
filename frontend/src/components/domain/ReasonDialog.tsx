"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { TextAreaField } from "@/components/ui/fields";
import { Modal } from "@/components/ui/Modal";

interface ReasonDialogProps {
  open: boolean;
  title: string;
  description?: string;
  confirmLabel: string;
  loading?: boolean;
  danger?: boolean;
  onConfirm: (reason: string) => void;
  onClose: () => void;
}

/** Confirmação que exige motivo (cancelamentos, estornos, anulações). O motivo fica registrado no histórico. */
export function ReasonDialog({
  open,
  title,
  description,
  confirmLabel,
  loading,
  danger = true,
  onConfirm,
  onClose,
}: ReasonDialogProps) {
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);
  const error = touched && !reason.trim() ? "Informe o motivo." : undefined;

  const close = () => {
    setReason("");
    setTouched(false);
    onClose();
  };

  return (
    <Modal
      open={open}
      title={title}
      onClose={close}
      size="sm"
      footer={
        <>
          <Button variant="outline" onClick={close} disabled={loading}>
            Voltar
          </Button>
          <Button
            variant={danger ? "danger" : "primary"}
            loading={loading}
            onClick={() => {
              setTouched(true);
              if (reason.trim()) onConfirm(reason.trim());
            }}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      {description && <p style={{ marginBottom: 16 }}>{description}</p>}
      <TextAreaField
        label="Motivo"
        required
        rows={3}
        maxLength={300}
        value={reason}
        error={error}
        onChange={(e) => setReason(e.target.value)}
      />
    </Modal>
  );
}
