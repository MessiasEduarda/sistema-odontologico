"use client";

import { useQuery } from "@tanstack/react-query";
import { Plus, Sparkles } from "lucide-react";
import { useState } from "react";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FormGrid, SelectField, TextAreaField } from "@/components/ui/fields";
import { ConfirmDialog, Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import type { ConsentCategory, ConsentTemplate } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { CONSENT_CATEGORIES } from "@/lib/labels";
import { useAction } from "@/lib/mutations";

export const CATEGORY_OPTIONS = Object.entries(CONSENT_CATEGORIES).map(([value, c]) => ({ value, label: c.label }));

const STARTER = `Eu, {{paciente}}, CPF {{cpf}}, declaro que fui informado(a) pelo(a) profissional {{profissional}} ({{cro}}) sobre o procedimento: {{procedimento}}, incluindo seus objetivos, alternativas, riscos, possíveis complicações e cuidados necessários, e que tive a oportunidade de esclarecer minhas dúvidas.

Declaro que as informações de saúde que prestei são verdadeiras e autorizo a realização do procedimento na {{clinica}}.

Data: {{data}}`;

/** Modelos de termo por categoria. Editar cria nova versão; termos já emitidos mantêm o texto assinado. */
export function ConsentTemplatesManager() {
  const [editing, setEditing] = useState<ConsentTemplate | "new" | null>(null);
  const [archiving, setArchiving] = useState<ConsentTemplate | null>(null);
  const [showOld, setShowOld] = useState(false);
  const [category, setCategory] = useState("");
  const list = useQuery({
    queryKey: ["consent-templates", showOld],
    queryFn: () => api.clinic.consentTemplates(showOld),
  });
  const archive = useAction(() => api.clinic.archiveConsentTemplate(archiving?.id ?? ""), {
    success: "Modelo arquivado.",
    invalidate: [["consent-templates"]],
    onSuccess: () => setArchiving(null),
  });
  const examples = useAction(() => api.clinic.addConsentExamples(), {
    success: (r) =>
      r.created > 0 ? `${r.created} modelo(s) de exemplo adicionado(s).` : "Os modelos de exemplo já estão na lista.",
    invalidate: [["consent-templates"]],
  });
  const rows = (list.data ?? []).filter((t) => !category || t.category === category);

  return (
    <Stack $gap={4}>
      <Alert tone="warning">
        Os modelos devem ser revisados por assessoria jurídica e pelo responsável técnico. Os modelos de exemplo trazem
        só a estrutura do termo; descreva os riscos e cuidados específicos de cada procedimento.
      </Alert>
      <Row $justify="space-between">
        <Row $gap={4}>
          <div style={{ width: 220 }}>
            <SelectField
              label="Categoria"
              placeholder="Todas"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              options={CATEGORY_OPTIONS}
            />
          </div>
          <CheckboxField
            label="Mostrar versões anteriores"
            checked={showOld}
            onChange={(e) => setShowOld(e.target.checked)}
          />
        </Row>
        <Row $gap={2}>
          <Button variant="outline" onClick={() => examples.mutate(undefined)} loading={examples.isPending}>
            <Sparkles size={16} aria-hidden /> Adicionar modelos de exemplo
          </Button>
          <Button onClick={() => setEditing("new")}>
            <Plus size={16} aria-hidden /> Novo modelo
          </Button>
        </Row>
      </Row>
      <DataTable<ConsentTemplate>
        caption="Modelos de termo"
        rows={list.data ? rows : undefined}
        loading={list.isPending}
        rowKey={(t) => t.id}
        emptyTitle="Nenhum modelo"
        emptyDescription="Crie um modelo ou adicione os modelos de exemplo para começar."
        columns={[
          { key: "title", header: "Título", render: (t) => <strong>{t.title}</strong> },
          {
            key: "category",
            header: "Categoria",
            render: (t) => (
              <Badge $tone={CONSENT_CATEGORIES[t.category]?.tone}>{CONSENT_CATEGORIES[t.category]?.label}</Badge>
            ),
          },
          { key: "version", header: "Versão", render: (t) => `v${t.version}` },
          { key: "date", header: "Criada em", hideOnMobile: true, render: (t) => formatDateTime(t.createdAt) },
          {
            key: "status",
            header: "Situação",
            render: (t) => (t.active ? <Badge $tone="success">Atual</Badge> : <Badge>Anterior</Badge>),
          },
          {
            key: "actions",
            header: "",
            render: (t) =>
              t.active && (
                <Row $gap={2} $wrap={false}>
                  <Button variant="ghost" onClick={() => setEditing(t)}>
                    Editar
                  </Button>
                  <Button variant="ghost" onClick={() => setArchiving(t)}>
                    Arquivar
                  </Button>
                </Row>
              ),
          },
        ]}
      />
      {editing && <TemplateForm template={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog
        open={Boolean(archiving)}
        title="Arquivar modelo"
        message="O modelo deixa de aparecer para emissão de novos termos. Termos já emitidos não mudam."
        confirmLabel="Arquivar"
        tone="danger"
        loading={archive.isPending}
        onConfirm={() => archive.mutate(undefined)}
        onClose={() => setArchiving(null)}
      />
    </Stack>
  );
}

function TemplateForm({ template, onClose }: { template: ConsentTemplate | null; onClose: () => void }) {
  const [title, setTitle] = useState(template?.title ?? "");
  const [category, setCategory] = useState<ConsentCategory>(template?.category ?? "DENTAL");
  const [body, setBody] = useState(template?.body ?? STARTER);
  const save = useAction(
    () =>
      template
        ? api.clinic.updateConsentTemplate(template.id, { title, category, body })
        : api.clinic.createConsentTemplate({ title, category, body }),
    {
      success: template ? "Nova versão criada." : "Modelo criado.",
      invalidate: [["consent-templates"]],
      onSuccess: onClose,
    },
  );
  return (
    <Modal
      open
      onClose={onClose}
      title={template ? `Editar modelo (nova versão de v${template.version})` : "Novo modelo de termo"}
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => save.mutate(undefined)}
            loading={save.isPending}
            disabled={!title.trim() || !body.trim()}
          >
            Salvar
          </Button>
        </>
      }
    >
      <Stack $gap={4}>
        <FormGrid $columns={2}>
          <TextField label="Título" required maxLength={160} value={title} onChange={(e) => setTitle(e.target.value)} />
          <SelectField
            label="Categoria"
            value={category}
            onChange={(e) => setCategory(e.target.value as ConsentCategory)}
            options={CATEGORY_OPTIONS}
          />
        </FormGrid>
        <TextAreaField
          label="Texto"
          required
          rows={16}
          maxLength={30000}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          hint="Campos preenchidos automaticamente: {{paciente}} {{cpf}} {{procedimento}} {{profissional}} {{cro}} {{clinica}} {{data}}"
        />
        {save.error && <Alert tone="danger">{save.error.message}</Alert>}
      </Stack>
    </Modal>
  );
}
