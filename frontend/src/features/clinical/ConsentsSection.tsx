"use client";

import { useQuery } from "@tanstack/react-query";
import { Copy, Link2, Printer } from "lucide-react";
import { useState } from "react";
import styled from "styled-components";
import { PdfButton } from "@/components/domain/PdfButton";
import { PrintSheet } from "@/components/domain/PrintSheet";
import { ReasonDialog } from "@/components/domain/ReasonDialog";
import { SignaturePad } from "@/components/domain/SignaturePad";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Muted, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, FullRow, SelectField } from "@/components/ui/fields";
import { LoadingState } from "@/components/ui/LoadingState";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { useFeedback } from "@/components/ui/Feedback";
import { useMe } from "@/features/auth/hooks";
import { api } from "@/lib/api/endpoints";
import type { Consent } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { CONSENT_CATEGORIES, CONSENT_STATUS } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { printSheet } from "@/lib/print";

/**
 * Termos de consentimento do paciente: emissão a partir de modelo, assinatura presencial (desenhada na tela) ou por
 * link enviado ao paciente, revogação e impressão com os dados da assinatura.
 */
export function ConsentsSection({ patientId, patientName }: { patientId: string; patientName: string }) {
  const can = useCan();
  const writable = can(P.CLINICAL_WRITE, P.PATIENTS_WRITE);
  const [issuing, setIssuing] = useState(false);
  const [openId, setOpenId] = useState<string | null>(null);
  const consents = useQuery({ queryKey: ["consents", patientId], queryFn: () => api.clinical.consents(patientId) });

  return (
    <Stack $gap={4}>
      <Row $justify="space-between">
        <Muted>O texto do termo fica congelado no momento da emissão; o paciente assina exatamente esse texto.</Muted>
        {writable && <Button onClick={() => setIssuing(true)}>Emitir termo</Button>}
      </Row>
      <DataTable<Consent>
        caption="Termos de consentimento"
        rows={consents.data}
        loading={consents.isPending}
        error={consents.error}
        onRetry={() => consents.refetch()}
        rowKey={(c) => c.id}
        onRowClick={(c) => setOpenId(c.id)}
        emptyTitle="Nenhum termo emitido"
        columns={[
          { key: "date", header: "Emissão", render: (c) => formatDateTime(c.createdAt) },
          { key: "title", header: "Termo", render: (c) => <strong>{c.title}</strong> },
          {
            key: "category",
            header: "Categoria",
            hideOnMobile: true,
            render: (c) => (
              <Badge $tone={CONSENT_CATEGORIES[c.category]?.tone}>{CONSENT_CATEGORIES[c.category]?.label}</Badge>
            ),
          },
          { key: "proc", header: "Procedimento", hideOnMobile: true, render: (c) => c.procedureDescription ?? "—" },
          {
            key: "status",
            header: "Situação",
            render: (c) => <Badge $tone={CONSENT_STATUS[c.status]?.tone}>{CONSENT_STATUS[c.status]?.label}</Badge>,
          },
          {
            key: "signed",
            header: "Assinado em",
            hideOnMobile: true,
            render: (c) => (c.signedAt ? formatDateTime(c.signedAt) : "—"),
          },
          {
            key: "pdf",
            header: "",
            render: (c) => (
              <PdfButton
                compact
                load={async () => ({ kind: "consent", patientId, data: await api.clinical.consent(patientId, c.id) })}
              />
            ),
          },
        ]}
      />
      {issuing && <IssueConsent patientId={patientId} onClose={() => setIssuing(false)} onIssued={setOpenId} />}
      {openId && (
        <ConsentDetail
          patientId={patientId}
          patientName={patientName}
          consentId={openId}
          writable={writable}
          onClose={() => setOpenId(null)}
        />
      )}
    </Stack>
  );
}

export function IssueConsent({
  patientId,
  onClose,
  onIssued,
}: {
  patientId: string;
  onClose: () => void;
  onIssued: (id: string) => void;
}) {
  const templates = useQuery({ queryKey: ["consent-templates"], queryFn: () => api.clinic.consentTemplates() });
  const professionals = useQuery({ queryKey: ["professionals", true], queryFn: () => api.clinic.professionals(true) });
  const [templateId, setTemplateId] = useState("");
  const [professionalId, setProfessionalId] = useState("");
  const [procedure, setProcedure] = useState("");
  const issue = useAction(
    () =>
      api.clinical.issueConsent(patientId, {
        templateId,
        professionalId: professionalId || null,
        procedureDescription: procedure || undefined,
      }),
    {
      success: "Termo emitido com sucesso!",
      invalidate: [["consents", patientId], ["record", patientId], ["all-consents"]],
      onSuccess: (c) => {
        onClose();
        onIssued(c.id);
      },
    },
  );
  const noTemplates = templates.data && templates.data.length === 0;

  return (
    <Modal
      open
      onClose={onClose}
      title="Emitir termo de consentimento"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => issue.mutate(undefined)} loading={issue.isPending} disabled={!templateId}>
            Emitir
          </Button>
        </>
      }
    >
      {noTemplates ? (
        <Alert tone="warning">Nenhum modelo de termo cadastrado. Crie na página Termos, aba Modelos.</Alert>
      ) : (
        <FormGrid $columns={1}>
          <SelectField
            label="Modelo"
            required
            placeholder="Selecione…"
            value={templateId}
            onChange={(e) => setTemplateId(e.target.value)}
            options={(templates.data ?? []).map((t) => ({
              value: t.id,
              label: `${CONSENT_CATEGORIES[t.category]?.label ?? ""} · ${t.title} (v${t.version})`,
            }))}
          />
          <SelectField
            label="Profissional responsável"
            placeholder="(opcional)"
            value={professionalId}
            onChange={(e) => setProfessionalId(e.target.value)}
            options={(professionals.data ?? []).map((p) => ({ value: p.id, label: p.name }))}
          />
          <TextField
            label="Procedimento"
            maxLength={200}
            value={procedure}
            onChange={(e) => setProcedure(e.target.value)}
            hint="Preenche o campo {{procedimento}} do modelo."
          />
          {issue.error && <Alert tone="danger">{issue.error.message}</Alert>}
        </FormGrid>
      )}
    </Modal>
  );
}

export function ConsentDetail({
  patientId,
  patientName,
  consentId,
  writable,
  onClose,
}: {
  patientId: string;
  patientName: string;
  consentId: string;
  writable: boolean;
  onClose: () => void;
}) {
  const feedback = useFeedback();
  const { data: me } = useMe();
  const consent = useQuery({
    queryKey: ["consent", consentId],
    queryFn: () => api.clinical.consent(patientId, consentId),
  });
  const [signing, setSigning] = useState(false);
  const [revoking, setRevoking] = useState(false);
  const [link, setLink] = useState<string | null>(null);
  const [sendEmail, setSendEmail] = useState(true);
  const [signer, setSigner] = useState({
    name: patientName,
    document: "",
    image: null as string | null,
    accepted: false,
  });

  const invalidate = [["consents", patientId], ["consent", consentId], ["record", patientId], ["all-consents"]];
  const sign = useAction(
    () =>
      api.clinical.signConsent(patientId, consentId, {
        signerName: signer.name,
        signerDocument: signer.document || undefined,
        signatureImage: signer.image ?? "",
        accepted: signer.accepted,
      }),
    { success: "Termo assinado com sucesso!", invalidate, onSuccess: () => setSigning(false) },
  );
  const makeLink = useAction(() => api.clinical.consentLink(patientId, consentId, sendEmail), {
    success: sendEmail
      ? "Link gerado e enviado com sucesso! (o e-mail só sai se o paciente permitir)"
      : "Link gerado com sucesso!",
    onSuccess: (r) => setLink(r.url),
  });
  const revoke = useAction((reason: string) => api.clinical.revokeConsent(patientId, consentId, reason), {
    success: "Termo revogado com sucesso!",
    invalidate,
    onSuccess: () => setRevoking(false),
  });

  const c = consent.data;

  return (
    <>
      <Modal
        open
        onClose={onClose}
        title="Termo de consentimento"
        size="lg"
        footer={
          c && (
            <>
              <Button variant="outline" onClick={printSheet}>
                <Printer size={16} aria-hidden /> Imprimir
              </Button>
              <PdfButton load={() => ({ kind: "consent", patientId, data: c })} />
              {writable && c.status === "PENDING" && (
                <>
                  <Button variant="outline" onClick={() => setRevoking(true)}>
                    Revogar
                  </Button>
                  <Button onClick={() => setSigning(true)}>Assinar agora (na clínica)</Button>
                </>
              )}
            </>
          )
        }
      >
        {!c ? (
          <LoadingState />
        ) : (
          <Stack $gap={4}>
            {c.status === "PENDING" && writable && (
              <Alert tone="warning" title="Aguardando assinatura do paciente">
                <Stack $gap={2}>
                  <span>
                    Assine agora na tela da clínica ou envie um link (válido por 7 dias) para o paciente assinar.
                  </span>
                  <Row $gap={3}>
                    <CheckboxField
                      label="Enviar por e-mail"
                      checked={sendEmail}
                      onChange={(e) => setSendEmail(e.target.checked)}
                    />
                    <Button variant="outline" onClick={() => makeLink.mutate(undefined)} loading={makeLink.isPending}>
                      <Link2 size={16} aria-hidden /> Gerar link de assinatura
                    </Button>
                  </Row>
                  {link && (
                    <LinkBox>
                      <code>{link}</code>
                      <Button
                        variant="ghost"
                        onClick={() =>
                          navigator.clipboard.writeText(link).then(() => feedback.success("Link copiado com sucesso!"))
                        }
                      >
                        <Copy size={16} aria-hidden /> Copiar
                      </Button>
                    </LinkBox>
                  )}
                </Stack>
              </Alert>
            )}
            {c.status === "REVOKED" && (
              <Alert tone="info">
                Revogado em {formatDateTime(c.revokedAt)}: {c.revokeReason}
              </Alert>
            )}
            <PrintSheet
              clinicName={me?.activeClinic?.name ?? ""}
              title={c.title}
              footer={
                c.status === "SIGNED" ? (
                  <SignatureBlock>
                    {/* Assinatura em data URL (PNG gerado no navegador); next/image não se aplica. */}
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    {c.signatureImage && <img src={c.signatureImage} alt={`Assinatura de ${c.signerName}`} />}
                    <p>
                      <strong>{c.signerName}</strong>
                      {c.signerDocument && ` · Doc.: ${c.signerDocument}`}
                    </p>
                    <p>
                      Assinado eletronicamente em {formatDateTime(c.signedAt)} · IP {c.signedIp ?? "—"}
                    </p>
                    <p>Integridade do texto (SHA-256): {c.bodySha256}</p>
                  </SignatureBlock>
                ) : (
                  <p>Situação: {CONSENT_STATUS[c.status]?.label}</p>
                )
              }
            >
              {c.body}
            </PrintSheet>
          </Stack>
        )}
      </Modal>

      <Modal
        open={signing}
        onClose={() => setSigning(false)}
        title="Assinatura do paciente"
        size="lg"
        footer={
          <>
            <Button data-modal-cancel variant="outline" onClick={() => setSigning(false)}>
              Cancelar
            </Button>
            <Button
              onClick={() => sign.mutate(undefined)}
              loading={sign.isPending}
              disabled={!signer.image || !signer.accepted || !signer.name.trim()}
            >
              Confirmar assinatura
            </Button>
          </>
        }
      >
        <Stack $gap={4}>
          <Muted>Entregue a tela ao paciente (ou responsável) para ler o termo e assinar.</Muted>
          <FormGrid $columns={2}>
            <TextField
              label="Nome de quem assina"
              required
              value={signer.name}
              onChange={(e) => setSigner((s) => ({ ...s, name: e.target.value }))}
            />
            <TextField
              label="Documento (CPF/RG)"
              value={signer.document}
              onChange={(e) => setSigner((s) => ({ ...s, document: e.target.value }))}
            />
            <FullRow>
              <SignaturePad onChange={(image) => setSigner((s) => ({ ...s, image }))} />
            </FullRow>
            <FullRow>
              <CheckboxField
                label="Declaro que li e compreendi o termo, tive minhas dúvidas esclarecidas e concordo com o seu conteúdo."
                checked={signer.accepted}
                onChange={(e) => setSigner((s) => ({ ...s, accepted: e.target.checked }))}
              />
            </FullRow>
          </FormGrid>
          {sign.error && <Alert tone="danger">{sign.error.message}</Alert>}
        </Stack>
      </Modal>

      <ReasonDialog
        open={revoking}
        title="Revogar termo"
        confirmLabel="Revogar"
        loading={revoke.isPending}
        onConfirm={(r) => revoke.mutate(r)}
        onClose={() => setRevoking(false)}
      />
    </>
  );
}

const LinkBox = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space[2]};
  flex-wrap: wrap;

  code {
    font-family: ${({ theme }) => theme.fonts.mono};
    font-size: ${({ theme }) => theme.fontSizes.xs};
    background: ${({ theme }) => theme.colors.surface};
    padding: 4px 8px;
    border-radius: 6px;
    word-break: break-all;
  }
`;

const SignatureBlock = styled.div`
  display: grid;
  gap: 4px;

  img {
    max-width: 320px;
    max-height: 120px;
    border-bottom: 1px solid #333;
  }
  p {
    font-size: ${({ theme }) => theme.fontSizes.xs};
    word-break: break-all;
  }
`;
