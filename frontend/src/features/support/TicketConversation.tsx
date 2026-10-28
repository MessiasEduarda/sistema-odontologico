"use client";

import { Download, FileText, Image as ImageIcon, Lock } from "lucide-react";
import styled, { css } from "styled-components";
import { Badge, Muted } from "@/components/ui/display";
import type { TicketAttachment, TicketDetail, TicketMessage } from "@/lib/api/types";
import { downloadUrl } from "@/lib/download";
import { formatDateTime } from "@/lib/format";
import { TICKET_PRIORITY, TICKET_STATUS } from "@/lib/labels";

export function TicketBadges({ ticket }: { ticket: Pick<TicketDetail, "status" | "priority" | "overdue"> }) {
  return (
    <>
      <Badge $tone={TICKET_STATUS[ticket.status]?.tone}>{TICKET_STATUS[ticket.status]?.label}</Badge>
      <Badge $tone={TICKET_PRIORITY[ticket.priority]?.tone}>{TICKET_PRIORITY[ticket.priority]?.label}</Badge>
      {ticket.overdue && <Badge $tone="danger">Prazo estourado</Badge>}
    </>
  );
}

export function TicketConversation({ messages, view }: { messages: TicketMessage[]; view: "clinic" | "support" }) {
  return (
    <Thread aria-label="Conversa do chamado">
      {messages.map((m) =>
        m.authorType === "SYSTEM" ? (
          <SystemLine key={m.id}>
            {m.body} <time dateTime={m.createdAt}>{formatDateTime(m.createdAt)}</time>
          </SystemLine>
        ) : (
          <Bubble key={m.id} $mine={(view === "clinic") === (m.authorType === "CLINIC")} $internal={m.internal}>
            <BubbleHead>
              <strong>{authorLabel(m, view)}</strong>
              {m.internal && (
                <InternalTag>
                  <Lock size={12} aria-hidden /> Nota interna
                </InternalTag>
              )}
              <time dateTime={m.createdAt}>{formatDateTime(m.createdAt)}</time>
            </BubbleHead>
            <BubbleText>{m.body}</BubbleText>
          </Bubble>
        ),
      )}
    </Thread>
  );
}

function authorLabel(m: TicketMessage, view: "clinic" | "support") {
  if (m.authorType === "SUPPORT") return view === "clinic" ? "Equipe de suporte" : `Suporte · ${m.authorName ?? ""}`;
  return m.authorName ?? "Clínica";
}

export function TicketAttachments({
  attachments,
  urlFor,
}: {
  attachments: TicketAttachment[];
  urlFor: (attachmentId: string) => string;
}) {
  if (attachments.length === 0) return <Muted>Nenhum anexo.</Muted>;
  return (
    <Files>
      {attachments.map((a) => (
        <li key={a.id}>
          {a.contentType.startsWith("image/") ? (
            <ImageIcon size={16} aria-hidden />
          ) : (
            <FileText size={16} aria-hidden />
          )}
          <span>
            {a.filename}
            <small>
              {a.authorType === "SUPPORT" ? "Suporte" : "Clínica"} · {formatSize(a.sizeBytes)} ·{" "}
              {formatDateTime(a.createdAt)}
            </small>
          </span>
          {a.purged ? (
            <Muted>Apagado</Muted>
          ) : (
            <DownloadButton
              type="button"
              onClick={() => downloadUrl(urlFor(a.id), a.filename)}
              aria-label={`Baixar ${a.filename}`}
              title="Baixar"
            >
              <Download size={16} aria-hidden />
            </DownloadButton>
          )}
        </li>
      ))}
    </Files>
  );
}

function formatSize(bytes: number) {
  return bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

const Thread = styled.ol`
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: ${({ theme }) => theme.space[3]};
`;

const Bubble = styled.li<{ $mine: boolean; $internal: boolean }>`
  max-width: min(720px, 92%);
  justify-self: ${({ $mine }) => ($mine ? "end" : "start")};
  padding: ${({ theme }) => `${theme.space[3]} ${theme.space[4]}`};
  border-radius: ${({ theme }) => theme.radii.lg};
  border: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme, $mine }) => ($mine ? theme.colors.primarySoft : theme.colors.surface)};
  ${({ $internal, theme }) =>
    $internal &&
    css`
      background: ${theme.colors.warningSoft};
      border-style: dashed;
      border-color: ${theme.colors.warning};
    `}
`;

const BubbleHead = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${({ theme }) => theme.space[2]};
  margin-bottom: ${({ theme }) => theme.space[1]};
  font-size: ${({ theme }) => theme.fontSizes.sm};

  time {
    color: ${({ theme }) => theme.colors.textMuted};
  }
`;

const InternalTag = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  color: ${({ theme }) => theme.colors.warning};
`;

const BubbleText = styled.p`
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
`;

const SystemLine = styled.li`
  justify-self: center;
  text-align: center;
  font-size: ${({ theme }) => theme.fontSizes.sm};
  color: ${({ theme }) => theme.colors.textMuted};

  time {
    margin-left: ${({ theme }) => theme.space[1]};
  }
`;

const Files = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: ${({ theme }) => theme.space[2]};

  li {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto;
    align-items: center;
    gap: ${({ theme }) => theme.space[3]};
    padding: ${({ theme }) => theme.space[2]} ${({ theme }) => theme.space[3]};
    border: 1px solid ${({ theme }) => theme.colors.border};
    border-radius: ${({ theme }) => theme.radii.md};
  }
  span {
    display: grid;
    min-width: 0;
    overflow-wrap: anywhere;
  }
  small {
    color: ${({ theme }) => theme.colors.textMuted};
  }
`;

const DownloadButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radii.pill};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.text};
  cursor: pointer;

  &:hover {
    border-color: ${({ theme }) => theme.colors.primary};
  }
  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
`;
