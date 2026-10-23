"use client";

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

export function ResetPasswordForm() {
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
      await authApi.resetPassword(token ?? "", password);
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
      {token === "" && <Alert tone="danger">Link inválido. Solicite uma nova redefinição de senha.</Alert>}
      {done ? (
        <Alert tone="success" title="Senha alterada">
          Por segurança, todas as sessões foram encerradas. Entre novamente com a nova senha.
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
              label="Confirme a nova senha"
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
        <TextLink href={done ? "/entrar" : "/esqueci-senha"}>
          {done ? "Ir para o login" : "Solicitar novo link"}
        </TextLink>
      </FormFooter>
    </AuthLayout>
  );
}
