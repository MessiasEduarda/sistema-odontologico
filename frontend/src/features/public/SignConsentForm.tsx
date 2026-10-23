"use client";

import { useState } from "react";
import { SignaturePad } from "@/components/domain/SignaturePad";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { CheckboxField, FormGrid, FullRow } from "@/components/ui/fields";
import { TextField } from "@/components/ui/TextField";
import { useRequiredFields } from "@/lib/useRequiredFields";
import type { SignatureInput } from "./api";

interface Props {
  defaultName: string;
  loading: boolean;
  error?: string;
  onSign: (input: SignatureInput) => void;
}

export function SignConsentForm({ defaultName, loading, error, onSign }: Props) {
  const [signerName, setSignerName] = useState(defaultName);
  const [signerDocument, setSignerDocument] = useState("");
  const [accepted, setAccepted] = useState(false);
  const [signature, setSignature] = useState<string | null>(null);
  const form = useRequiredFields({
    name: !signerName.trim() && "Informe o nome de quem assina.",
    signature: !signature && "Faça a sua assinatura no quadro acima.",
    accepted: !accepted && "É necessário declarar que leu e concorda com o termo.",
  });
  return (
    <FormGrid $columns={2}>
      <TextField
        label="Nome de quem assina"
        required
        error={form.errors.name}
        maxLength={160}
        value={signerName}
        onChange={(e) => setSignerName(e.target.value)}
      />
      <TextField
        label="CPF de quem assina"
        maxLength={20}
        hint="Opcional. Informe se for responsável legal."
        value={signerDocument}
        onChange={(e) => setSignerDocument(e.target.value)}
      />
      <FullRow>
        <SignaturePad error={form.errors.signature} onChange={setSignature} />
      </FullRow>
      <FullRow>
        <CheckboxField
          label="Li o termo acima e concordo com o seu conteúdo."
          checked={accepted}
          error={form.errors.accepted}
          onChange={(e) => setAccepted(e.target.checked)}
        />
      </FullRow>
      {error && (
        <FullRow>
          <Alert tone="danger">{error}</Alert>
        </FullRow>
      )}
      <FullRow>
        <Button
          fullWidth
          loading={loading}
          onClick={() =>
            form.check() &&
            signature &&
            onSign({
              signerName: signerName.trim(),
              signerDocument: signerDocument.trim() || undefined,
              signatureImage: signature,
              accepted,
            })
          }
        >
          Assinar termo
        </Button>
      </FullRow>
    </FormGrid>
  );
}
