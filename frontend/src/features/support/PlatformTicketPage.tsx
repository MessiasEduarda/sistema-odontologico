"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Paperclip } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import {
  Card,
  CardTitle,
  DetailList,
  ErrorState,
  PageHeader,
  PermissionDenied,
  Row,
  Stack,
} from "@/components/ui/display";
import { useFeedback } from "@/components/ui/Feedback";
import { CheckboxField, FieldError, SelectField, TextAreaField } from "@/components/ui/fields";
import { LoadingState } from "@/components/ui/LoadingState";
import { useMe } from "@/features/auth/hooks";
import { api } from "@/lib/api/endpoints";
import type { TicketDetail, TicketPriority, TicketStatus } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { TICKET_PRIORITY, ticketCategoryLabel } from "@/lib/labels";
import { useAction } from "@/lib/mutations";
import { useRequiredFields } from "@/lib/useRequiredFields";
import { TicketAttachments, TicketBadges, TicketConversation } from "./TicketConversation";

const MAX_FILE_BYTES = 5 * 1024 * 1024;

const AFTER_REPLY = [
  { value: "WAITING_CLINIC", label: "Aguardar a clínica" },
  { value: "IN_PROGRESS", label: "Continuar em análise" },
  { value: "RESOLVED", label: "Marcar como resolvido" },
];

const PRIORITY_OPTIONS = Object.entries(TICKET_PRIORITY).map(([value, p]) => ({ value, label: p.label }));

export function PlatformTicketPage({ ticketId }: { ticketId: string }) {
  const { data: me } = useMe();
  const ticket = useQuery({
    queryKey: ["platform-support-ticket", ticketId],
    queryFn: () => api.platform.supportTicket(ticketId),
    enabled: Boolean(me?.platformAdmin),
    refetchInterval: 60_000,
  });
  const queryClient = useQueryClient();
  const loadedAt = ticket.dataUpdatedAt;

  useEffect(() => {
    if (!loadedAt) return;
    void queryClient.invalidateQueries({ queryKey: ["platform-support-unread"] });
    void queryClient.invalidateQueries({ queryKey: ["platform-support-tickets"] });
    void queryClient.invalidateQueries({ queryKey: ["support-metrics"] });
  }, [loadedAt, queryClient]);

  if (me && !me.platformAdmin) return <PermissionDenied description="Área exclusiva da administração da plataforma." />;
  if (ticket.isPending) return <LoadingState />;
  if (ticket.isError) return <ErrorState error={ticket.error} onRetry={ticket.refetch} />;
  const t = ticket.data;

  return (
    <Stack>
      <BackLink href="/plataforma?aba=suporte">
        <ArrowLeft size={16} aria-hidden /> Voltar à fila
      </BackLink>
      <PageHeader
        title={t.subject}
        description={`${t.protocol} · ${t.clinicName} · ${ticketCategoryLabel(t.category)}`}
      />
      <Row $gap={2}>
        <TicketBadges ticket={t} />
      </Row>

      <Layout>
        <Stack $gap={4}>
          <Card>
            <TicketConversation messages={t.messages} view="support" />
          </Card>
          {t.canReply ? (
            <ReplyForm ticket={t} />
          ) : (
            <Alert tone="info">Chamado encerrado em {formatDateTime(t.closedAt)}. Não aceita novas mensagens.</Alert>
          )}
        </Stack>

        <Stack $gap={4}>
          <TicketActions ticket={t} />
          <Card>
            <CardTitle>Dados do chamado</CardTitle>
            <DetailList>
              <div>
                <dt>Clínica</dt>
                <dd>{t.clinicName}</dd>
              </div>
              <div>
                <dt>Aberto por</dt>
                <dd>
                  {t.openedByName}
                  <br />
                  <a href={`mailto:${t.openedByEmail}`}>{t.openedByEmail}</a>
                </dd>
              </div>
              <div>
                <dt>Aberto em</dt>
                <dd>{formatDateTime(t.createdAt)}</dd>
              </div>
              <div>
                <dt>Primeira resposta</dt>
                <dd>
                  {t.firstResponseAt
                    ? formatDateTime(t.firstResponseAt)
                    : `prazo até ${formatDateTime(t.firstResponseDueAt)}`}
                </dd>
              </div>
              {t.page && (
                <div>
                  <dt>Tela</dt>
                  <dd>
                    <code>{t.page}</code>
                  </dd>
                </div>
              )}
              {t.traceId && (
                <div>
                  <dt>Código do erro (traceId)</dt>
                  <dd>
                    <code>{t.traceId}</code>
                  </dd>
                </div>
              )}
              {t.errorMessage && (
                <div>
                  <dt>Mensagem exibida</dt>
                  <dd>{t.errorMessage}</dd>
                </div>
              )}
              {t.userAgent && (
                <div>
                  <dt>Navegador</dt>
                  <dd>
                    <small>{t.userAgent}</small>
                  </dd>
                </div>
              )}
              {t.relatedProtocol && (
                <div>
                  <dt>Continua o chamado</dt>
                  <dd>
                    <Link href={`/plataforma/suporte/${t.relatedTicketId}`}>{t.relatedProtocol}</Link>
                  </dd>
                </div>
              )}
              {t.rating != null && (
                <div>
                  <dt>Avaliação</dt>
                  <dd>
                    {t.rating} / 5{t.ratingComment ? ` · ${t.ratingComment}` : ""}
                  </dd>
                </div>
              )}
            </DetailList>
          </Card>
          <Card>
            <CardTitle>Anexos</CardTitle>
            <Alert tone="warning">Abrir um anexo fica registrado na auditoria (pode conter dados de pacientes).</Alert>
            <TicketAttachments
              attachments={t.attachments}
              urlFor={(attachmentId) => api.platform.supportAttachmentUrl(t.id, attachmentId)}
            />
          </Card>
        </Stack>
      </Layout>
    </Stack>
  );
}

function TicketActions({ ticket }: { ticket: TicketDetail }) {
  const feedback = useFeedback();
  const invalidate = [["platform-support-ticket", ticket.id], ["platform-support-tickets"], ["support-metrics"]];
  const status = useAction((next: TicketStatus) => api.platform.supportStatus(ticket.id, next), {
    success: "Situação do chamado atualizada!",
    invalidate,
  });
  const priority = useAction((next: TicketPriority) => api.platform.supportPriority(ticket.id, next), {
    success: "Prioridade atualizada! O prazo de primeira resposta foi recalculado.",
    invalidate,
  });
  if (!ticket.canReply) return null;
  return (
    <Card>
      <CardTitle>Ações</CardTitle>
      <Stack $gap={3}>
        <SelectField
          label="Prioridade"
          value={ticket.priority}
          onChange={(e) => priority.mutate(e.target.value as TicketPriority)}
          options={PRIORITY_OPTIONS}
        />
        <Row $gap={2}>
          {ticket.status !== "RESOLVED" && (
            <Button variant="outline" onClick={() => status.mutate("RESOLVED")} loading={status.isPending}>
              Marcar como resolvido
            </Button>
          )}
          <Button
            variant="ghost"
            onClick={async () => {
              const confirmed = await feedback.confirm({
                message: "Encerrar este chamado?",
                detail: "Chamado encerrado não recebe mais mensagens. Se precisar, a clínica abre outro chamado.",
                confirmLabel: "Encerrar",
                danger: true,
              });
              if (confirmed) status.mutate("CLOSED");
            }}
            loading={status.isPending}
          >
            Encerrar
          </Button>
        </Row>
      </Stack>
    </Card>
  );
}

function ReplyForm({ ticket }: { ticket: TicketDetail }) {
  const [body, setBody] = useState("");
  const [cannedId, setCannedId] = useState("");
  const [internal, setInternal] = useState(false);
  const [after, setAfter] = useState("WAITING_CLINIC");
  const [fileError, setFileError] = useState<string>();
  const fileInput = useRef<HTMLInputElement>(null);
  const canned = useQuery({
    queryKey: ["canned-responses", "active"],
    queryFn: () => api.platform.cannedResponses(true),
  });
  const form = useRequiredFields({ body: !body.trim() && "Escreva a mensagem." });
  const invalidate = [["platform-support-ticket", ticket.id], ["platform-support-tickets"], ["support-metrics"]];

  const reply = useAction(
    () =>
      api.platform.supportReply(ticket.id, {
        body: body.trim(),
        internal,
        status: internal ? null : (after as TicketStatus),
      }),
    {
      success: internal ? "Nota interna registrada!" : "Resposta enviada! A clínica recebe um aviso por e-mail.",
      invalidate,
      onSuccess: () => {
        setBody("");
        setCannedId("");
        form.reset();
      },
    },
  );
  const attach = useAction((file: File) => api.platform.supportAttach(ticket.id, file), {
    success: "Arquivo anexado com sucesso!",
    invalidate,
  });

  return (
    <Card>
      <Stack $gap={3}>
        {(canned.data?.length ?? 0) > 0 && (
          <SelectField
            label="Usar resposta pronta"
            placeholder="Escolha para preencher o texto…"
            value={cannedId}
            onChange={(e) => {
              const chosen = canned.data?.find((c) => c.id === e.target.value);
              setCannedId(chosen?.id ?? "");
              if (chosen) {
                setBody(chosen.body);
                form.reset();
              }
            }}
            options={(canned.data ?? []).map((c) => ({ value: c.id, label: c.title }))}
          />
        )}
        <TextAreaField
          label={internal ? "Nota interna (a clínica não vê)" : "Resposta para a clínica"}
          required
          rows={5}
          maxLength={5000}
          error={form.errors.body}
          value={body}
          onChange={(e) => setBody(e.target.value)}
        />
        <CheckboxField
          label="Nota interna (só a equipe da plataforma vê)"
          checked={internal}
          onChange={(e) => setInternal(e.target.checked)}
        />
        {!internal && (
          <SelectField
            label="Depois de responder"
            value={after}
            onChange={(e) => setAfter(e.target.value)}
            options={AFTER_REPLY}
          />
        )}
        {fileError && <FieldError>{fileError}</FieldError>}
        <Row $justify="space-between">
          <span>
            <input
              ref={fileInput}
              type="file"
              hidden
              accept="image/png,image/jpeg,image/webp,application/pdf"
              onChange={(e) => {
                const file = e.target.files?.[0];
                e.target.value = "";
                setFileError(undefined);
                if (!file) return;
                if (file.size > MAX_FILE_BYTES) setFileError("O arquivo pode ter no máximo 5 MB.");
                else attach.mutate(file);
              }}
            />
            <Button variant="outline" onClick={() => fileInput.current?.click()} loading={attach.isPending}>
              <Paperclip size={16} aria-hidden /> Anexar arquivo
            </Button>
          </span>
          <Button onClick={() => form.check() && reply.mutate(undefined)} loading={reply.isPending}>
            {internal ? "Salvar nota" : "Enviar resposta"}
          </Button>
        </Row>
      </Stack>
    </Card>
  );
}

const BackLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: ${({ theme }) => theme.space[2]};
  width: fit-content;
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  color: ${({ theme }) => theme.colors.primaryText};
`;

const Layout = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[4]};
  grid-template-columns: minmax(0, 1fr);

  @media (min-width: ${({ theme }) => theme.breakpoints.lg}px) {
    grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
    align-items: start;
  }
`;
