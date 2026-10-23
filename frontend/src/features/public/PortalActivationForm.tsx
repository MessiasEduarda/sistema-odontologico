"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { CheckboxField } from "@/components/ui/fields";
import { TextField } from "@/components/ui/TextField";
import { AuthLayout, Form, FormFooter } from "@/features/auth/AuthLayout";
import { ErrorMessage, TextLink } from "@/features/auth/LoginForm";
import { passwordProblem } from "@/features/auth/passwordSchema";
import { errorMessage } from "@/lib/forms";
import { useHashToken } from "@/lib/useHashToken";
import { publicApi } from "./api";

export function PortalActivationForm() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const token = useHashToken();
  const messageId = useId();
  const [birthDate, setBirthDate] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [accepted, setAccepted] = useState(false);
  const [message, setMessage] = useState<string>();
  const [pending, setPending] = useState(false);

  const lookup = useQuery({
    queryKey: ["portal-activation", token],
    queryFn: () => publicApi.portalActivationLookup(token ?? ""),
    enabled: Boolean(token),
    retry: false,
  });

  const submit = async () => {
    const problem =
      (!birthDate && "Informe a sua data de nascimento.") ||
      passwordProblem(password, confirm) ||
      (!accepted && "É necessário aceitar os Termos de Uso e a Política de Privacidade.");
    if (problem) return setMessage(problem);
    setMessage(undefined);
    setPending(true);
    try {
      await publicApi.portalActivate({ token: token ?? "", birthDate, password, acceptedTerms: accepted });
      queryClient.clear();
      router.replace("/portal");
    } catch (error) {
      setMessage(errorMessage(error, "Não foi possível criar a senha."));
    } finally {
      setPending(false);
    }
  };

  const invalid = message ? { "aria-invalid": true, "aria-describedby": messageId } : {};

  return (
    <AuthLayout
      title="Criar sua senha"
      subtitle={
        lookup.data
          ? `Olá, ${lookup.data.firstName}! A ${lookup.data.clinicName} criou o seu acesso ao portal do paciente.`
          : undefined
      }
    >
      {(token === "" || lookup.isError) && (
        <Alert tone="danger">
          {lookup.error?.message ?? "Este link é inválido, expirou ou já foi usado. Peça um novo convite à clínica."}
        </Alert>
      )}
      {lookup.isPending && token && <p aria-live="polite">Carregando convite…</p>}
      {lookup.data && (
        <Form
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
          noValidate
        >
          <TextField label="E-mail de acesso" value={lookup.data.email} disabled readOnly />
          <TextField
            label="Data de nascimento"
            type="date"
            value={birthDate}
            onChange={(e) => setBirthDate(e.target.value)}
            {...invalid}
          />
          <TextField
            label="Crie uma senha"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            {...invalid}
          />
          <TextField
            label="Repita a senha"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            {...invalid}
          />
          <CheckboxField
            label="Li e aceito os Termos de Uso e a Política de Privacidade"
            checked={accepted}
            onChange={(e) => setAccepted(e.target.checked)}
          />
          {message && (
            <ErrorMessage id={messageId} role="alert">
              {message}
            </ErrorMessage>
          )}
          <Button type="submit" loading={pending} fullWidth>
            Criar senha e entrar
          </Button>
        </Form>
      )}
      <FormFooter>
        <TextLink href="/portal/entrar">Já tenho senha</TextLink>
      </FormFooter>
    </AuthLayout>
  );
}
