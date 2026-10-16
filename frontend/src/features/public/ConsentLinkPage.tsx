"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert } from "@/components/ui/Alert";
import { Card, CardTitle } from "@/components/ui/display";
import { LoadingState } from "@/components/ui/LoadingState";
import { formatDateTime } from "@/lib/format";
import { useHashToken } from "@/lib/useHashToken";
import { publicApi, type SignatureInput } from "./api";
import { ConsentText, PublicLayout } from "./PublicLayout";
import { SignConsentForm } from "./SignConsentForm";

export function ConsentLinkPage() {
  const token = useHashToken();
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ["public-consent", token],
    queryFn: () => publicApi.consent(token ?? ""),
    enabled: Boolean(token),
    retry: false,
  });
  const sign = useMutation({
    mutationFn: (input: SignatureInput) => publicApi.signConsent(token ?? "", input),
    onSuccess: (data) => client.setQueryData(["public-consent", token], data),
  });
  const c = query.data;
  return (
    <PublicLayout clinicName={c?.clinicName} title="Termo de consentimento">
      {(token === "" || query.isError) && (
        <Alert tone="danger">{query.error?.message ?? "Este link é inválido ou expirou."}</Alert>
      )}
      {(token === null || (token && query.isPending)) && <LoadingState />}
      {c && (
        <Card>
          <CardTitle>{c.title}</CardTitle>
          <p style={{ marginBottom: 12 }}>Paciente: {c.patientName}</p>
          <ConsentText>{c.body}</ConsentText>
          <div style={{ marginTop: 20 }}>
            {c.status === "SIGNED" ? (
              <Alert tone="success" title="Termo assinado">
                Assinado em {formatDateTime(c.signedAt)}. Uma cópia fica disponível na clínica.
              </Alert>
            ) : c.status === "PENDING" ? (
              <SignConsentForm
                defaultName={c.patientName}
                loading={sign.isPending}
                error={sign.error?.message}
                onSign={sign.mutate}
              />
            ) : (
              <Alert tone="info">Este termo não está mais disponível para assinatura.</Alert>
            )}
          </div>
        </Card>
      )}
    </PublicLayout>
  );
}
