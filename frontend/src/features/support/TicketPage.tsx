"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CheckCircle2, Paperclip, Star } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Card, CardTitle, DetailList, ErrorState, PageHeader, Row, Stack } from "@/components/ui/display";
import { FieldError, TextAreaField } from "@/components/ui/fields";
import { LoadingState } from "@/components/ui/LoadingState";
import { api } from "@/lib/api/endpoints";
import type { TicketDetail } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { ticketCategoryLabel } from "@/lib/labels";
import { useAction } from "@/lib/mutations";
import { useRequiredFields } from "@/lib/useRequiredFields";
import { NewTicketForm } from "./NewTicketForm";
import { TicketAttachments, TicketBadges, TicketConversation } from "./TicketConversation";

const MAX_FILE_BYTES = 5 * 1024 * 1024;

export function TicketPage({ ticketId }: { ticketId: string }) {
  const ticket = useQuery({
    queryKey: ["support-ticket", ticketId],
    queryFn: () => api.support.ticket(ticketId),
    refetchInterval: 60_000,
  });
  const [followUp, setFollowUp] = useState(false);
  const queryClient = useQueryClient();
  const loadedAt = ticket.dataUpdatedAt;

  useEffect(() => {
    if (!loadedAt) return;
    void queryClient.invalidateQueries({ queryKey: ["support-unread"] });
    void queryClient.invalidateQueries({ queryKey: ["support-tickets"] });
  }, [loadedAt, queryClient]);

  if (ticket.isPending) return <LoadingState />;
  if (ticket.isError) return <ErrorState error={ticket.error} onRetry={ticket.refetch} />;
  const t = ticket.data;

  return (
    <Stack>
      <BackLink href="/suporte">
        <ArrowLeft size={16} aria-hidden /> Voltar aos chamados
      </BackLink>
      <PageHeader title={t.subject} description={`${t.protocol} · ${ticketCategoryLabel(t.category)}`} />
      <Row $gap={2}>
        <TicketBadges ticket={t} />
      </Row>

      <Layout>
        <Stack $gap={4}>
          <Card>
            <TicketConversation messages={t.messages} view="clinic" />
          </Card>
          {t.canReply ? (
            <ReplyBox ticket={t} />
          ) : (
            <Alert tone="info" title="Chamado encerrado">
              Se o problema voltou, abra um novo chamado ligado a este.{" "}
              <Button variant="outline" onClick={() => setFollowUp(true)}>
                Abrir chamado relacionado
              </Button>
            </Alert>
          )}
          {t.canRate && <RatingCard ticket={t} />}
        </Stack>

        <Stack $gap={4}>
          <Card>
            <CardTitle>Resumo</CardTitle>
            <DetailList>
              <div>
                <dt>Aberto por</dt>
                <dd>{t.openedByName}</dd>
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
                    : `prevista até ${formatDateTime(t.firstResponseDueAt)}`}
                </dd>
              </div>
              {t.resolvedAt && (
                <div>
                  <dt>Resolvido em</dt>
                  <dd>{formatDateTime(t.resolvedAt)}</dd>
                </div>
              )}
              {t.relatedProtocol && (
                <div>
                  <dt>Continua o chamado</dt>
                  <dd>
                    <Link href={`/suporte/${t.relatedTicketId}`}>{t.relatedProtocol}</Link>
                  </dd>
                </div>
              )}
              {t.traceId && (
                <div>
                  <dt>Código do erro</dt>
                  <dd>
                    <code>{t.traceId}</code>
                  </dd>
                </div>
              )}
            </DetailList>
          </Card>
          <Card>
            <CardTitle>Anexos</CardTitle>
            <TicketAttachments
              attachments={t.attachments}
              urlFor={(attachmentId) => api.support.attachmentUrl(t.id, attachmentId)}
            />
          </Card>
        </Stack>
      </Layout>

      {followUp && <NewTicketForm related={{ id: t.id, protocol: t.protocol }} onClose={() => setFollowUp(false)} />}
    </Stack>
  );
}

function ReplyBox({ ticket }: { ticket: TicketDetail }) {
  const [body, setBody] = useState("");
  const fileInput = useRef<HTMLInputElement>(null);
  const [fileError, setFileError] = useState<string>();
  const form = useRequiredFields({ body: !body.trim() && "Escreva a mensagem." });
  const invalidate = [["support-ticket", ticket.id], ["support-tickets"], ["support-unread"]];

  const reply = useAction(() => api.support.reply(ticket.id, body.trim()), {
    success: ticket.status === "RESOLVED" ? "Mensagem enviada e chamado reaberto!" : "Mensagem enviada com sucesso!",
    invalidate,
    onSuccess: () => {
      setBody("");
      form.reset();
    },
  });
  const attach = useAction((file: File) => api.support.attach(ticket.id, file), {
    success: "Arquivo anexado com sucesso!",
    invalidate,
  });
  const resolve = useAction(() => api.support.resolve(ticket.id), {
    success: "Obrigado! O chamado foi marcado como resolvido.",
    invalidate,
  });

  const pick = (file: File | undefined) => {
    setFileError(undefined);
    if (!file) return;
    if (file.size > MAX_FILE_BYTES) {
      setFileError("O arquivo pode ter no máximo 5 MB.");
      return;
    }
    attach.mutate(file);
  };

  return (
    <Card>
      <Stack $gap={3}>
        {ticket.status === "RESOLVED" && (
          <Alert tone="success">
            Este chamado foi resolvido. Se o problema continuar, responda abaixo que ele é reaberto.
          </Alert>
        )}
        <TextAreaField
          label="Sua mensagem"
          required
          rows={4}
          maxLength={5000}
          error={form.errors.body}
          value={body}
          onChange={(e) => setBody(e.target.value)}
        />
        {fileError && <FieldError>{fileError}</FieldError>}
        <Row $justify="space-between">
          <Row $gap={2}>
            <input
              ref={fileInput}
              type="file"
              hidden
              accept="image/png,image/jpeg,image/webp,application/pdf"
              onChange={(e) => {
                pick(e.target.files?.[0]);
                e.target.value = "";
              }}
            />
            <Button variant="outline" onClick={() => fileInput.current?.click()} loading={attach.isPending}>
              <Paperclip size={16} aria-hidden /> Anexar print
            </Button>
            {ticket.status !== "RESOLVED" && (
              <Button variant="ghost" onClick={() => resolve.mutate(undefined)} loading={resolve.isPending}>
                <CheckCircle2 size={16} aria-hidden /> Problema resolvido
              </Button>
            )}
          </Row>
          <Button onClick={() => form.check() && reply.mutate(undefined)} loading={reply.isPending}>
            Enviar mensagem
          </Button>
        </Row>
      </Stack>
    </Card>
  );
}

function RatingCard({ ticket }: { ticket: TicketDetail }) {
  const [rating, setRating] = useState(ticket.rating ?? 0);
  const [comment, setComment] = useState(ticket.ratingComment ?? "");
  const form = useRequiredFields({ rating: rating === 0 && "Escolha uma nota de 1 a 5." });
  const rate = useAction(() => api.support.rate(ticket.id, rating, comment.trim() || undefined), {
    success: "Obrigado pela avaliação!",
    invalidate: [["support-ticket", ticket.id]],
  });
  return (
    <Card>
      <CardTitle>{ticket.rating ? "Sua avaliação" : "Como foi o atendimento?"}</CardTitle>
      <Stack $gap={3}>
        <Stars role="radiogroup" aria-label="Nota do atendimento">
          {[1, 2, 3, 4, 5].map((n) => (
            <StarButton
              key={n}
              type="button"
              role="radio"
              aria-checked={rating === n}
              aria-label={`${n} de 5`}
              $on={n <= rating}
              onClick={() => setRating(n)}
            >
              <Star size={26} aria-hidden />
            </StarButton>
          ))}
        </Stars>
        {form.errors.rating && <FieldError>{form.errors.rating}</FieldError>}
        <TextAreaField
          label="Comentário (opcional)"
          rows={2}
          maxLength={500}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
        />
        <Row $justify="flex-end">
          <Button onClick={() => form.check() && rate.mutate(undefined)} loading={rate.isPending}>
            {ticket.rating ? "Atualizar avaliação" : "Enviar avaliação"}
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
    grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
    align-items: start;
  }
`;

const Stars = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.space[1]};
`;

const StarButton = styled.button<{ $on: boolean }>`
  display: inline-flex;
  padding: 4px;
  border: 0;
  border-radius: ${({ theme }) => theme.radii.sm};
  background: transparent;
  cursor: pointer;
  color: ${({ theme, $on }) => ($on ? theme.colors.primary : theme.colors.border)};

  svg {
    fill: ${({ $on }) => ($on ? "currentColor" : "none")};
  }
  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
`;
