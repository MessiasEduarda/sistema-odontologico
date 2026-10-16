"use client";

import { useQuery } from "@tanstack/react-query";
import { RotateCcw } from "lucide-react";
import { useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Card, CardTitle, Muted, PageHeader, Row, Stack } from "@/components/ui/display";
import { CheckboxField, SelectField, TextAreaField } from "@/components/ui/fields";
import { IconButton } from "@/components/ui/IconButton";
import { LoadingState } from "@/components/ui/LoadingState";
import { Tabs } from "@/components/ui/Tabs";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { MessageTemplate, OutboundMessage } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { MESSAGE_STATUS, MESSAGE_TYPES } from "@/lib/labels";
import { useAction } from "@/lib/mutations";

export function CommunicationPage() {
  const [tab, setTab] = useState("historico");
  return (
    <Stack>
      <PageHeader
        title="Comunicação"
        description="Lembretes e avisos enviados por e-mail aos pacientes que autorizaram contato."
      />
      <Tabs
        label="Comunicação"
        active={tab}
        onChange={setTab}
        items={[
          { id: "historico", label: "Histórico de envios", content: <MessagesTab /> },
          { id: "modelos", label: "Modelos de mensagem", content: <TemplatesTab /> },
        ]}
      />
    </Stack>
  );
}

function MessagesTab() {
  const [status, setStatus] = useState("");
  const { data, isPending, error, refetch } = useQuery({
    queryKey: ["messages", status],
    queryFn: () => api.communication.messages(status || undefined),
    refetchInterval: 30_000,
  });
  const retry = useAction(api.communication.retry, {
    success: "Mensagem reenfileirada com sucesso!",
    invalidate: [["messages"]],
  });
  return (
    <Stack $gap={4}>
      <Filter>
        <SelectField
          label="Situação"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          placeholder="Todas"
          options={Object.entries(MESSAGE_STATUS).map(([value, s]) => ({ value, label: s.label }))}
        />
      </Filter>
      <DataTable<OutboundMessage>
        caption="Mensagens enviadas"
        rows={data}
        loading={isPending}
        error={error}
        onRetry={refetch}
        rowKey={(m) => m.id}
        emptyTitle="Nenhuma mensagem"
        emptyDescription="Os lembretes são gerados automaticamente antes das consultas."
        columns={[
          { key: "date", header: "Criada em", render: (m) => formatDateTime(m.createdAt) },
          { key: "type", header: "Tipo", render: (m) => MESSAGE_TYPES[m.type] ?? m.type },
          { key: "patient", header: "Paciente", render: (m) => m.patientName ?? "—" },
          { key: "to", header: "Para", hideOnMobile: true, render: (m) => m.toAddress },
          {
            key: "status",
            header: "Situação",
            render: (m) => (
              <span title={m.lastError ?? undefined}>
                <Badge $tone={MESSAGE_STATUS[m.status]?.tone}>{MESSAGE_STATUS[m.status]?.label}</Badge>
                {m.status === "FAILED" && <Muted> {m.attempts} tentativa(s)</Muted>}
              </span>
            ),
          },
          {
            key: "sent",
            header: "Enviada em",
            hideOnMobile: true,
            render: (m) => (m.sentAt ? formatDateTime(m.sentAt) : "—"),
          },
          {
            key: "retry",
            header: "",
            render: (m) =>
              m.status === "FAILED" && (
                <IconButton icon={RotateCcw} label="Tentar enviar de novo" onClick={() => retry.mutate(m.id)} />
              ),
          },
        ]}
      />
    </Stack>
  );
}

function TemplatesTab() {
  const { data, isPending } = useQuery({ queryKey: ["message-templates"], queryFn: api.communication.templates });
  if (isPending) return <LoadingState />;
  return (
    <Stack $gap={4}>
      <Alert tone="info">
        Campos disponíveis: {"{{paciente}} {{clinica}} {{data}} {{hora}} {{profissional}} {{link}} {{titulo}}"}.
        Mensagens só são enviadas a pacientes com e-mail cadastrado que aceitam contato por e-mail.
      </Alert>
      {data?.map((t) => (
        <TemplateEditor key={t.type} template={t} />
      ))}
    </Stack>
  );
}

function TemplateEditor({ template }: { template: MessageTemplate }) {
  const [subject, setSubject] = useState(template.subject);
  const [body, setBody] = useState(template.body);
  const [active, setActive] = useState(template.active);
  const save = useAction(() => api.communication.saveTemplate(template.type, { subject, body, active }), {
    success: "Modelo salvo com sucesso!",
    invalidate: [["message-templates"]],
  });
  const dirty = subject !== template.subject || body !== template.body || active !== template.active;
  return (
    <Card>
      <CardTitle>
        <span>
          {MESSAGE_TYPES[template.type] ?? template.type} {!template.custom && <Badge>Padrão do sistema</Badge>}
        </span>
        <CheckboxField label="Ativo" checked={active} onChange={(e) => setActive(e.target.checked)} />
      </CardTitle>
      <Stack $gap={3}>
        <TextField label="Assunto" maxLength={200} value={subject} onChange={(e) => setSubject(e.target.value)} />
        <TextAreaField
          label="Mensagem"
          rows={6}
          maxLength={5000}
          value={body}
          onChange={(e) => setBody(e.target.value)}
        />
        <Row $justify="flex-end">
          <Button
            onClick={() => save.mutate(undefined)}
            loading={save.isPending}
            disabled={!dirty || !subject.trim() || !body.trim()}
          >
            Salvar modelo
          </Button>
        </Row>
      </Stack>
    </Card>
  );
}

const Filter = styled.div`
  width: 200px;
`;
