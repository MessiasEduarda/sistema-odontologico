"use client";

import { Download, FileSpreadsheet } from "lucide-react";
import { useRef, useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Muted, Stack } from "@/components/ui/display";
import { Modal } from "@/components/ui/Modal";
import { api } from "@/lib/api/endpoints";
import type { PatientImportResult, PatientImportRow } from "@/lib/api/types";
import { normalizeHeader, parseCsv, readCsvFile, toCsv } from "@/lib/csv";
import { useAction } from "@/lib/mutations";

type Field = Exclude<keyof PatientImportRow, "line">;

/** Colunas aceitas: nome do modelo (primeiro) e outros nomes comuns em planilhas de clínicas. */
const COLUMNS: { field: Field; header: string; aliases: string[] }[] = [
  { field: "fullName", header: "Nome completo", aliases: ["nome", "nome do paciente", "paciente"] },
  { field: "socialName", header: "Nome social", aliases: [] },
  { field: "birthDate", header: "Data de nascimento", aliases: ["nascimento", "data nascimento", "dt nascimento"] },
  { field: "cpf", header: "CPF", aliases: [] },
  { field: "rg", header: "RG", aliases: [] },
  { field: "sex", header: "Sexo", aliases: ["genero"] },
  { field: "phone", header: "Telefone", aliases: ["fone", "telefone fixo"] },
  { field: "mobile", header: "Celular", aliases: ["whatsapp", "telefone celular"] },
  { field: "email", header: "E-mail", aliases: ["email"] },
  { field: "zipCode", header: "CEP", aliases: [] },
  { field: "street", header: "Endereço", aliases: ["rua", "logradouro"] },
  { field: "number", header: "Número", aliases: ["numero", "n"] },
  { field: "complement", header: "Complemento", aliases: [] },
  { field: "district", header: "Bairro", aliases: [] },
  { field: "city", header: "Cidade", aliases: ["municipio"] },
  { field: "state", header: "UF", aliases: ["estado"] },
  { field: "occupation", header: "Profissão", aliases: ["ocupacao"] },
  { field: "referralSource", header: "Como conheceu a clínica", aliases: ["origem", "indicacao", "como conheceu"] },
  { field: "healthInsurance", header: "Convênio", aliases: ["plano"] },
  { field: "insuranceNumber", header: "Número do convênio", aliases: ["carteirinha", "numero da carteirinha"] },
  { field: "guardianName", header: "Responsável", aliases: ["nome do responsavel", "responsavel legal"] },
  { field: "guardianCpf", header: "CPF do responsável", aliases: [] },
  { field: "guardianPhone", header: "Telefone do responsável", aliases: [] },
  { field: "notes", header: "Observações", aliases: ["obs", "observacao"] },
  { field: "allowEmail", header: "Aceita e-mail", aliases: ["aceita email"] },
  { field: "allowWhatsapp", header: "Aceita WhatsApp", aliases: [] },
];

const HEADER_TO_FIELD = new Map<string, Field>(
  COLUMNS.flatMap((c) => [c.header, ...c.aliases].map((h) => [normalizeHeader(h), c.field] as const)),
);

/** Converte a planilha em linhas para a API. Retorna também as colunas que não foram reconhecidas. */
export function toImportRows(table: string[][]): { rows: PatientImportRow[]; unknown: string[] } {
  const [header = [], ...body] = table;
  const fields = header.map((h) => HEADER_TO_FIELD.get(normalizeHeader(h)));
  const unknown = header.filter((h, i) => h.trim() && !fields[i]);
  const rows = body.map((cells, index) => {
    const row: PatientImportRow = { line: index + 2 };
    fields.forEach((field, i) => {
      if (field && cells[i]?.trim()) row[field] = cells[i].trim();
    });
    return row;
  });
  return { rows, unknown };
}

function downloadTemplate() {
  const example = [
    "Paciente Exemplo (fictício)",
    "",
    "15/03/1990",
    "",
    "",
    "F",
    "",
    "(11) 90000-0000",
    "exemplo@email.com",
    "01000-000",
    "Rua Exemplo",
    "100",
    "",
    "Centro",
    "São Paulo",
    "SP",
    "",
    "Indicação",
    "",
    "",
    "",
    "",
    "",
    "",
    "sim",
    "sim",
  ];
  const blob = new Blob([toCsv([COLUMNS.map((c) => c.header), example])], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "modelo-importacao-pacientes.csv";
  a.click();
  URL.revokeObjectURL(url);
}

/** Importação de pacientes: modelo, leitura da planilha, prévia com problemas por linha e confirmação. */
export function ImportPatients({ onClose }: { onClose: () => void }) {
  const input = useRef<HTMLInputElement>(null);
  const [fileName, setFileName] = useState<string>();
  const [rows, setRows] = useState<PatientImportRow[]>([]);
  const [unknown, setUnknown] = useState<string[]>([]);
  const [localError, setLocalError] = useState<string>();
  const [preview, setPreview] = useState<PatientImportResult>();

  const check = useAction((list: PatientImportRow[]) => api.patients.importRows(list, true), {
    onSuccess: (result) => setPreview(result),
  });
  const confirm = useAction(() => api.patients.importRows(rows, false), {
    success: (r) => `${r.created} paciente(s) importado(s) com sucesso!`,
    invalidate: [["patients"]],
    onSuccess: onClose,
  });

  const pick = async (file: File | undefined) => {
    setLocalError(undefined);
    setPreview(undefined);
    if (!file) return;
    setFileName(file.name);
    if (!/\.(csv|txt)$/i.test(file.name)) {
      setLocalError("Envie a planilha em CSV. No Excel: Arquivo › Salvar como › CSV (separado por ponto e vírgula).");
      return;
    }
    const parsed = toImportRows(parseCsv(await readCsvFile(file)));
    if (!parsed.rows.length) {
      setLocalError("A planilha está vazia ou só tem o cabeçalho.");
      return;
    }
    if (!parsed.rows.some((r) => r.fullName)) {
      setLocalError("Não encontrei a coluna de nome. Use o modelo ou nomeie a coluna como “Nome completo”.");
      return;
    }
    setRows(parsed.rows);
    setUnknown(parsed.unknown);
    check.mutate(parsed.rows);
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="Importar pacientes"
      size="lg"
      confirmDiscard={false}
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            onClick={() => confirm.mutate(undefined)}
            loading={confirm.isPending}
            disabled={!preview || preview.ready === 0}
          >
            {preview ? `Importar ${preview.ready} paciente(s)` : "Importar"}
          </Button>
        </>
      }
    >
      <Stack $gap={4}>
        <Muted>
          Traga os pacientes que a clínica já tem. Use o modelo (abre no Excel), preencha uma linha por paciente e salve
          como CSV. Só o nome é obrigatório; menores de 18 anos precisam do responsável. CPFs já cadastrados não são
          duplicados.
        </Muted>
        <Actions>
          <Button variant="outline" onClick={downloadTemplate}>
            <Download size={16} aria-hidden /> Baixar modelo
          </Button>
          <input
            ref={input}
            type="file"
            accept=".csv,text/csv"
            hidden
            onChange={(e) => {
              void pick(e.target.files?.[0]);
              e.target.value = "";
            }}
          />
          <Button onClick={() => input.current?.click()} loading={check.isPending}>
            <FileSpreadsheet size={16} aria-hidden /> Escolher planilha
          </Button>
          {fileName && <Muted>{fileName}</Muted>}
        </Actions>

        {localError && <Alert tone="danger">{localError}</Alert>}
        {unknown.length > 0 && <Alert tone="info">Colunas ignoradas (não reconhecidas): {unknown.join(", ")}.</Alert>}

        {preview && (
          <>
            <Summary>
              <Badge $tone="success">{preview.ready} pronto(s) para importar</Badge>
              {preview.issues.length > 0 && <Badge $tone="danger">{preview.issues.length} com problema</Badge>}
              <Muted>de {preview.total} linha(s) na planilha</Muted>
            </Summary>
            {preview.issues.length > 0 && (
              <DataTable
                caption="Linhas com problema"
                rows={preview.issues}
                rowKey={(i) => `${i.line}`}
                columns={[
                  { key: "line", header: "Linha", render: (i) => i.line },
                  { key: "name", header: "Paciente", render: (i) => i.name || "—" },
                  {
                    key: "kind",
                    header: "Situação",
                    render: (i) => (
                      <Badge $tone={i.kind === "DUPLICATE" ? "warning" : "danger"}>
                        {i.kind === "DUPLICATE" ? "Já cadastrado" : "Erro"}
                      </Badge>
                    ),
                  },
                  { key: "msg", header: "Motivo", render: (i) => i.message },
                ]}
              />
            )}
            {preview.issues.length > 0 && (
              <Muted>
                As linhas com problema não entram. Corrija na planilha e importe de novo depois — quem já entrou não é
                duplicado.
              </Muted>
            )}
          </>
        )}
      </Stack>
    </Modal>
  );
}

const Actions = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${({ theme }) => theme.space[3]};
`;

const Summary = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${({ theme }) => theme.space[2]};
`;
