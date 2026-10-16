"use client";

import { useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { CheckboxField, FormGrid, FullRow, SelectField, TextAreaField } from "@/components/ui/fields";
import { TextField } from "@/components/ui/TextField";
import { ApiError } from "@/lib/api/client";
import type { Patient, PatientInput } from "@/lib/api/types";
import { ageFrom, formatCpf, isValidCpf, onlyDigits } from "@/lib/format";
import { SEX_OPTIONS, UF_OPTIONS } from "@/lib/labels";

const EMPTY: PatientInput = {
  fullName: "",
  socialName: null,
  birthDate: null,
  cpf: null,
  rg: null,
  sex: "NOT_INFORMED",
  phone: null,
  mobile: null,
  email: null,
  zipCode: null,
  street: null,
  number: null,
  complement: null,
  district: null,
  city: null,
  state: null,
  occupation: null,
  referralSource: null,
  healthInsurance: null,
  insuranceNumber: null,
  guardianName: null,
  guardianCpf: null,
  guardianPhone: null,
  notes: null,
  allowEmail: true,
  allowSms: false,
  allowWhatsapp: false,
};

export function toInput(p: Patient): PatientInput {
  const rest: PatientInput = { ...EMPTY };
  for (const key of Object.keys(EMPTY) as (keyof PatientInput)[])
    (rest as Record<string, unknown>)[key] = p[key as keyof Patient];
  return {
    ...rest,
    cpf: p.cpf ? formatCpf(p.cpf) : null,
    guardianCpf: p.guardianCpf ? formatCpf(p.guardianCpf) : null,
  };
}

interface Props {
  initial?: PatientInput;
  saving: boolean;
  serverError: Error | null;
  submitLabel: string;
  onSubmit: (input: PatientInput) => void;
  onCancel?: () => void;
}

/** Cadastro completo do paciente. Validações espelham as do servidor (que é a autoridade final). */
export function PatientForm({ initial = EMPTY, saving, serverError, submitLabel, onSubmit, onCancel }: Props) {
  const [v, setV] = useState<PatientInput>(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const set = <K extends keyof PatientInput>(k: K, value: PatientInput[K]) => setV((s) => ({ ...s, [k]: value }));
  const text = (k: keyof PatientInput) => ({
    value: (v[k] as string | null) ?? "",
    onChange: (e: { target: { value: string } }) => set(k, (e.target.value || null) as never),
    error: errors[k as string] ?? fieldError(serverError, k as string),
  });

  const age = ageFrom(v.birthDate);
  const minor = age !== null && age < 18;

  const submit = () => {
    const e: Record<string, string> = {};
    if (!v.fullName.trim()) e.fullName = "Informe o nome completo.";
    if (v.cpf && !isValidCpf(v.cpf)) e.cpf = "CPF inválido.";
    if (v.guardianCpf && !isValidCpf(v.guardianCpf)) e.guardianCpf = "CPF inválido.";
    if (v.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.email)) e.email = "E-mail inválido.";
    if (minor && !v.guardianName?.trim()) e.guardianName = "Informe o responsável legal (menor de 18).";
    if (v.birthDate && new Date(v.birthDate) > new Date()) e.birthDate = "Data no futuro.";
    setErrors(e);
    if (Object.keys(e).length === 0) onSubmit({ ...v, cpf: v.cpf ? onlyDigits(v.cpf) : null });
  };

  const general = serverError && (!(serverError instanceof ApiError) || serverError.fieldErrors.length === 0);

  return (
    <Form
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
      noValidate
    >
      <Section>
        <legend>Identificação</legend>
        <FormGrid $columns={3}>
          <FullRow>
            <TextField label="Nome completo" required maxLength={160} {...text("fullName")} />
          </FullRow>
          <TextField label="Nome social" maxLength={160} {...text("socialName")} />
          <TextField
            label="Data de nascimento"
            type="date"
            {...text("birthDate")}
            hint={age !== null ? `${age} ano(s)` : undefined}
          />
          <SelectField
            label="Sexo"
            value={v.sex}
            onChange={(e) => set("sex", e.target.value as PatientInput["sex"])}
            options={SEX_OPTIONS}
          />
          <TextField label="CPF" inputMode="numeric" maxLength={14} placeholder="000.000.000-00" {...text("cpf")} />
          <TextField label="RG" maxLength={20} {...text("rg")} />
          <TextField label="Profissão" maxLength={100} {...text("occupation")} />
        </FormGrid>
      </Section>

      <Section>
        <legend>Contato</legend>
        <FormGrid $columns={3}>
          <TextField label="Celular" type="tel" maxLength={20} placeholder="(11) 90000-0000" {...text("mobile")} />
          <TextField label="Telefone" type="tel" maxLength={20} {...text("phone")} />
          <TextField label="E-mail" type="email" maxLength={254} {...text("email")} />
        </FormGrid>
        <Prefs>
          <CheckboxField
            label="Aceita receber e-mails (lembretes, termos)"
            checked={v.allowEmail}
            onChange={(e) => set("allowEmail", e.target.checked)}
          />
          <CheckboxField label="Aceita SMS" checked={v.allowSms} onChange={(e) => set("allowSms", e.target.checked)} />
          <CheckboxField
            label="Aceita WhatsApp"
            checked={v.allowWhatsapp}
            onChange={(e) => set("allowWhatsapp", e.target.checked)}
          />
        </Prefs>
      </Section>

      <Section>
        <legend>Endereço</legend>
        <FormGrid $columns={3}>
          <TextField label="CEP" inputMode="numeric" maxLength={9} {...text("zipCode")} />
          <FullRowSpan2>
            <TextField label="Rua / avenida" maxLength={160} {...text("street")} />
          </FullRowSpan2>
          <TextField label="Número" maxLength={20} {...text("number")} />
          <TextField label="Complemento" maxLength={80} {...text("complement")} />
          <TextField label="Bairro" maxLength={80} {...text("district")} />
          <TextField label="Cidade" maxLength={100} {...text("city")} />
          <SelectField
            label="UF"
            placeholder="—"
            value={v.state ?? ""}
            onChange={(e) => set("state", e.target.value || null)}
            options={UF_OPTIONS}
          />
        </FormGrid>
      </Section>

      <Section>
        <legend>Convênio e origem</legend>
        <FormGrid $columns={3}>
          <TextField label="Convênio" maxLength={100} {...text("healthInsurance")} />
          <TextField label="Nº da carteirinha" maxLength={50} {...text("insuranceNumber")} />
          <TextField label="Como conheceu a clínica" maxLength={100} {...text("referralSource")} />
        </FormGrid>
      </Section>

      <Section>
        <legend>Responsável legal {minor ? "(obrigatório: paciente menor de 18)" : "(opcional)"}</legend>
        <FormGrid $columns={3}>
          <TextField label="Nome do responsável" maxLength={160} required={minor} {...text("guardianName")} />
          <TextField label="CPF do responsável" inputMode="numeric" maxLength={14} {...text("guardianCpf")} />
          <TextField label="Telefone do responsável" type="tel" maxLength={20} {...text("guardianPhone")} />
        </FormGrid>
      </Section>

      <TextAreaField label="Observações" rows={3} maxLength={4000} {...text("notes")} />

      {general && <Alert tone="danger">{serverError?.message}</Alert>}

      <Footer>
        {onCancel && (
          <Button data-modal-cancel variant="outline" onClick={onCancel} disabled={saving}>
            Cancelar
          </Button>
        )}
        <Button type="submit" loading={saving}>
          {submitLabel}
        </Button>
      </Footer>
    </Form>
  );
}

function fieldError(error: Error | null, field: string) {
  return error instanceof ApiError ? error.fieldErrors.find((f) => f.field === field)?.message : undefined;
}

const Form = styled.form`
  display: grid;
  gap: ${({ theme }) => theme.space[5]};
`;

const Section = styled.fieldset`
  border: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: ${({ theme }) => theme.space[3]};

  legend {
    font-weight: ${({ theme }) => theme.fontWeights.bold};
    color: ${({ theme }) => theme.colors.primaryText};
    margin-bottom: ${({ theme }) => theme.space[3]};
    padding: 0;
  }
`;

const FullRowSpan2 = styled.div`
  @media (min-width: ${({ theme }) => theme.breakpoints.md}px) {
    grid-column: span 2;
  }
`;

const Prefs = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.space[5]};
`;

const Footer = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: ${({ theme }) => theme.space[3]};
`;
