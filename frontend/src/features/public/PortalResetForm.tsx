"use client";

import { useId, useState } from "react";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { AuthLayout, Form, FormFooter } from "@/features/auth/AuthLayout";
import { ErrorMessage, TextLink } from "@/features/auth/LoginForm";
import { passwordProblem } from "@/features/auth/passwordSchema";
import { errorMessage } from "@/lib/forms";
import { useHashToken } from "@/lib/useHashToken";
import { publicApi } from "./api";

export function PortalResetForm() {
  const token = useHashToken();
  const messageId = useId();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [message, setMessage] = useState<string>();
  const [pending, setPending] = useState(false);
  const [done, setDone] = useState(false);

  const submit = async () => {
    const problem = passwordProblem(password, confirm);
    if (problem) return setMessage(problem);
    setMessage(undefined);
    setPending(true);
    try {
      await publicApi.portalResetPassword(token ?? "", password);
      setDone(true);
    } catch (error) {
      setMessage(errorMessage(error, "Não foi possível alterar a senha."));
    } finally {
      setPending(false);
    }
  };

  const invalid = message ? { "aria-invalid": true, "aria-describedby": messageId } : {};

  return (
    <AuthLayout title="Criar nova senha">
      {token === "" && <Alert tone="danger">Link inválido. Peça uma nova redefinição de senha.</Alert>}
      {done ? (
        <Alert tone="success" title="Senha alterada">
          Por segurança, todos os acessos abertos foram encerrados. Entre novamente com a nova senha.
        </Alert>
      ) : (
        token && (
          <Form
            onSubmit={(e) => {
              e.preventDefault();
              void submit();
            }}
            noValidate
          >
            <TextField
              label="Nova senha"
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
            {message && (
              <ErrorMessage id={messageId} role="alert">
                {message}
              </ErrorMessage>
            )}
            <Button type="submit" loading={pending} fullWidth>
              Salvar nova senha
            </Button>
          </Form>
        )
      )}
      <FormFooter>
        <TextLink href="/portal/entrar">Ir para o login</TextLink>
      </FormFooter>
    </AuthLayout>
  );
}
