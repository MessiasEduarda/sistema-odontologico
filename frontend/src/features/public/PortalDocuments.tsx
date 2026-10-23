"use client";

import { useQuery } from "@tanstack/react-query";
import { Download, FileText, Image as ImageIcon } from "lucide-react";
import { useState, type ReactNode } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Badge, Card, CardTitle, EmptyState, Muted } from "@/components/ui/display";
import { LoadingState } from "@/components/ui/LoadingState";
import { Tabs } from "@/components/ui/Tabs";
import type { ClinicSettings, Patient, ReceiptData } from "@/lib/api/types";
import { downloadUrl } from "@/lib/download";
import { formatDate, formatDateTime, formatMoney } from "@/lib/format";
import { ATTACHMENT_CATEGORIES, DOCUMENT_TYPES, methodLabel, PLAN_STATUS } from "@/lib/labels";
import type { PdfContext } from "@/lib/pdf/documents";
import { downloadPdf, type PdfDocument } from "@/lib/pdf/render";
import { publicApi, type PortalRecords } from "./api";

interface Entry {
  key: string;
  icon: "file" | "image";
  title: string;
  detail: ReactNode;
  download: () => Promise<void>;
}

export function PortalDocuments() {
  const query = useQuery({
    queryKey: ["portal-records"],
    queryFn: publicApi.portalRecords,
    retry: false,
  });
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<string | null>(null);

  if (query.isPending) {
    return (
      <Card>
        <CardTitle>Meus documentos</CardTitle>
        <LoadingState />
      </Card>
    );
  }
  if (query.isError) {
    return (
      <Card>
        <CardTitle>Meus documentos</CardTitle>
        <Alert tone="danger">{query.error.message}</Alert>
      </Card>
    );
  }

  const groups = buildGroups(query.data);
  const run = async (entry: Entry) => {
    setBusy(entry.key);
    setError(null);
    try {
      await entry.download();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Não foi possível baixar o arquivo.");
    } finally {
      setBusy(null);
    }
  };

  return (
    <Card>
      <CardTitle>Meus documentos</CardTitle>
      {error && <Alert tone="danger">{error}</Alert>}
      {groups.length === 0 ? (
        <EmptyState
          title="Nenhum documento disponível ainda"
          description="Receitas, atestados, termos assinados, orçamentos, recibos e exames liberados pela clínica aparecem aqui."
        />
      ) : (
        <Tabs
          label="Meus documentos"
          active={groups.some((g) => g.id === tab) ? (tab ?? "") : (groups[0]?.id ?? "")}
          onChange={setTab}
          items={groups.map((group) => ({
            id: group.id,
            label: `${group.title} (${group.entries.length})`,
            content: (
              <List>
                {group.entries.map((entry) => (
                  <Item key={entry.key}>
                    <IconBox aria-hidden="true">{entry.icon === "image" ? <ImageIcon /> : <FileText />}</IconBox>
                    <div>
                      <strong>{entry.title}</strong>
                      <Muted>{entry.detail}</Muted>
                    </div>
                    <Button variant="outline" onClick={() => void run(entry)} loading={busy === entry.key}>
                      <Download size={16} aria-hidden /> Baixar
                    </Button>
                  </Item>
                ))}
              </List>
            ),
          }))}
        />
      )}
    </Card>
  );
}

function buildGroups(records: PortalRecords): { id: string; title: string; entries: Entry[] }[] {
  const ctx: PdfContext = {
    clinic: { ...records.clinic } as ClinicSettings,
    patient: { ...records.patient } as Patient,
    logo: null,
  };
  const pdf = (doc: PdfDocument) => () => downloadPdf(doc, ctx);

  const receipts = records.receivables.flatMap((r) =>
    r.installments.flatMap((i) =>
      i.payments.map((p) => {
        const receipt: ReceiptData = {
          ...p,
          description: r.description,
          installment: r.installments.length > 1 ? `${i.number}/${r.installments.length}` : null,
        };
        return receipt;
      }),
    ),
  );

  const groups: { id: string; title: string; entries: Entry[] }[] = [
    {
      id: "exames",
      title: "Exames",
      entries: records.attachments.map((a) => ({
        key: `attachment-${a.id}`,
        icon: a.contentType.startsWith("image/") ? "image" : "file",
        title: a.description ?? a.filename,
        detail: (
          <>
            <Badge $tone="primary">{ATTACHMENT_CATEGORIES.find((c) => c.value === a.category)?.label}</Badge> ·{" "}
            {formatDate(a.createdAt)}
          </>
        ),
        download: async () => downloadUrl(publicApi.portalAttachmentUrl(a.id), a.filename),
      })),
    },
    {
      id: "documentos",
      title: "Receitas e atestados",
      entries: records.documents.map((d) => ({
        key: `document-${d.id}`,
        icon: "file",
        title: d.title,
        detail: `${DOCUMENT_TYPES.find((t) => t.value === d.type)?.label ?? "Documento"} · ${d.professionalName} · ${formatDate(d.issuedAt)}`,
        download: pdf({ kind: "document", data: d }),
      })),
    },
    {
      id: "termos",
      title: "Termos assinados",
      entries: records.consents.map((c) => ({
        key: `consent-${c.id}`,
        icon: "file",
        title: c.title,
        detail: `Assinado em ${formatDateTime(c.signedAt)}`,
        download: pdf({ kind: "consent", data: c }),
      })),
    },
    {
      id: "orcamentos",
      title: "Orçamentos",
      entries: records.plans.map((p) => ({
        key: `plan-${p.id}`,
        icon: "file",
        title: p.title,
        detail: `${PLAN_STATUS[p.status]?.label ?? p.status} · ${formatMoney(p.total)} · ${formatDate(p.createdAt)}`,
        download: pdf({ kind: "plan", data: p }),
      })),
    },
    {
      id: "recibos",
      title: "Recibos",
      entries: receipts.map((r) => ({
        key: `receipt-${r.id}`,
        icon: "file",
        title: `${formatMoney(r.amount)} · ${r.description}${r.installment ? ` (parcela ${r.installment})` : ""}`,
        detail: `${methodLabel(r.method)} · ${formatDateTime(r.paidAt)}`,
        download: pdf({ kind: "receipt", data: r }),
      })),
    },
  ];
  return groups.filter((g) => g.entries.length > 0);
}

const List = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: ${({ theme }) => theme.space[2]};
`;

const Item = styled.li`
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: ${({ theme }) => theme.space[3]};
  padding: ${({ theme }) => theme.space[3]};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radii.md};

  div {
    display: grid;
    gap: 2px;
    min-width: 0;
  }
  strong {
    overflow-wrap: anywhere;
  }

  @media (max-width: ${({ theme }) => theme.breakpoints.sm}px) {
    grid-template-columns: auto minmax(0, 1fr);

    & > button {
      grid-column: 1 / -1;
    }
  }
`;

const IconBox = styled.span`
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: ${({ theme }) => theme.radii.md};
  background: ${({ theme }) => theme.colors.primarySoft};
  color: ${({ theme }) => theme.colors.primaryText};
`;
