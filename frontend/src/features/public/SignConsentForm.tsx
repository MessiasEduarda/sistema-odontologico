"use client";

import { useState } from "react";
import { SignaturePad } from "@/components/domain/SignaturePad";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { CheckboxField, FormGrid, FullRow } from "@/components/ui/fields";
import { TextField } from "@/components/ui/TextField";
import type { SignatureInput } from "./api";

interface Props {
  defaultName: string;
  loading: boolean;
  error?: string;
  onSign: (input: SignatureInput) => void;
}

/** Nome, documento opcional, declaração de leitura e assinatura desenhada (ou digitada). */
export function SignConsentForm({ defaultName, loading, error, onSign }: Props) {
  const [signerName, setSignerName] = useState(defaultName);
  const [signerDocument, setSignerDocument] = useState("");
  const [accepted, setAccepted] = useState(false);
  const [signature, setSignature] = useState<string | null>(null);
  return (
    <FormGrid $columns={2}>
      <TextField
        label="Nome de quem assina"
        required
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
        <SignaturePad onChange={setSignature} />
      </FullRow>
      <FullRow>
        <CheckboxField
          label="Li o termo acima e concordo com o seu conteúdo."
          checked={accepted}
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
          disabled={!accepted || !signature || !signerName.trim()}
          onClick={() =>
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
