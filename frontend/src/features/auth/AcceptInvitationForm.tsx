"use client";

import { useQuery } from "@tanstack/react-query";
import { useId, useState } from "react";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { errorMessage } from "@/lib/forms";
import { useHashToken } from "@/lib/useHashToken";
import { authApi } from "./api";
import { AuthLayout, Form, FormFooter } from "./AuthLayout";
import { ErrorMessage, TextLink } from "./LoginForm";
import { passwordProblem } from "./passwordSchema";

export function AcceptInvitationForm() {
  const token = useHashToken();
  const messageId = useId();
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [message, setMessage] = useState<string>();
  const [pending, setPending] = useState(false);
  const [accepted, setAccepted] = useState(false);

  const preview = useQuery({
    queryKey: ["invitation", token],
    queryFn: () => authApi.previewInvitation(token ?? ""),
    enabled: Boolean(token),
    retry: false,
  });

  const existing = preview.data?.existingAccount ?? false;

  const submit = async () => {
    const problem = existing
      ? !password && "Informe a sua senha atual."
      : (!name.trim() && "Informe seu nome.") || passwordProblem(password, confirm);
    if (problem) return setMessage(problem);
    setMessage(undefined);
    setPending(true);
    try {
      await authApi.acceptInvitation(token ?? "", password, existing ? undefined : name.trim());
      setAccepted(true);
    } catch (error) {
      setMessage(errorMessage(error, "Não foi possível aceitar o convite."));
    } finally {
      setPending(false);
    }
  };

  const invalid = message ? { "aria-invalid": true, "aria-describedby": messageId } : {};
  const subtitle = preview.data
    ? `Você foi convidado(a) para a ${preview.data.clinicName} como ${preview.data.roleName}.`
    : undefined;

  return (
    <AuthLayout title="Aceitar convite" subtitle={subtitle}>
      {(token === "" || preview.isError) && (
        <Alert tone="danger">{preview.error?.message ?? "Este convite é inválido, expirou ou já foi utilizado."}</Alert>
      )}
      {preview.isPending && token && <p aria-live="polite">Carregando convite…</p>}

      {accepted ? (
        <Alert tone="success" title="Tudo certo!">
          Seu acesso foi liberado. Entre com seu e-mail e senha para começar.
        </Alert>
      ) : (
        preview.data && (
          <Form
            onSubmit={(e) => {
              e.preventDefault();
              void submit();
            }}
            noValidate
          >
            <TextField label="E-mail" value={preview.data.email} disabled readOnly />
            {existing ? (
              <Alert tone="info">Este e-mail já tem uma conta. Confirme com a senha atual para aceitar.</Alert>
            ) : (
              <TextField
                label="Seu nome"
                autoComplete="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                {...invalid}
              />
            )}
            <TextField
              label={existing ? "Senha atual" : "Crie uma senha"}
              type="password"
              autoComplete={existing ? "current-password" : "new-password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              {...invalid}
            />
            {!existing && (
              <TextField
                label="Repita a senha"
                type="password"
                autoComplete="new-password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                {...invalid}
              />
            )}
            {message && (
              <ErrorMessage id={messageId} role="alert">
                {message}
              </ErrorMessage>
            )}
            <Button type="submit" loading={pending} fullWidth>
              Aceitar convite
            </Button>
          </Form>
        )
      )}
      <FormFooter>
        <TextLink href="/entrar">Ir para o login</TextLink>
      </FormFooter>
    </AuthLayout>
  );
}
