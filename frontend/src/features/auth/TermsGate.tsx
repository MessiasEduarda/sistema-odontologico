"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Alert } from "@/components/ui/Alert";
import { CheckboxField } from "@/components/ui/fields";
import { ApiError } from "@/lib/api/client";
import { authApi } from "./api";
import { ME_KEY, useMe } from "./hooks";
import { DialogButton, LegalDialog } from "./LegalDialog";

/** Tempo depois de entrar no sistema até o modal aparecer. */
const DELAY_MS = 3000;

/**
 * Pede o aceite dos Termos de uso e da Política de privacidade uma única vez por pessoa (por versão dos termos).
 * Aparece alguns segundos após entrar; não fecha por fora nem com Esc: só segue após marcar "Li e aceito".
 */
export function TermsGate() {
  const { data: me } = useMe();
  const pending = Boolean(me && !me.termsAccepted);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!pending) return;
    const timer = window.setTimeout(() => setOpen(true), DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [pending]);

  if (!pending || !open || !me) return null;
  return <TermsDialog version={me.termsVersion} />;
}

function TermsDialog({ version }: { version: string }) {
  const queryClient = useQueryClient();
  const [agreed, setAgreed] = useState(false);

  const accept = useMutation({
    mutationFn: () => authApi.acceptTerms(version),
    onSuccess: (me) => queryClient.setQueryData(ME_KEY, me),
  });
  const outdated = accept.error instanceof ApiError && accept.error.status === 409;

  return (
    <LegalDialog
      subtitle="Leia e aceite para continuar usando o sistema"
      footer={
        <>
          {accept.isError && (
            <Alert tone="danger">
              {outdated
                ? "Os termos foram atualizados. Recarregue a página para ler a versão vigente."
                : "Não foi possível registrar o aceite. Tente novamente."}
            </Alert>
          )}
          <CheckboxField
            label="Li e aceito os Termos de Uso e a Política de Privacidade"
            checked={agreed}
            onChange={(e) => setAgreed(e.target.checked)}
          />
          <DialogButton
            type="button"
            disabled={!agreed || accept.isPending}
            aria-busy={accept.isPending || undefined}
            onClick={() => accept.mutate()}
          >
            {accept.isPending ? "Registrando…" : "Aceitar e continuar"}
          </DialogButton>
        </>
      }
    />
  );
}
