"use client";

import { useId, useState } from "react";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { AuthLayout, Form, FormFooter } from "@/features/auth/AuthLayout";
import { ErrorMessage, TextLink } from "@/features/auth/LoginForm";
import { EMAIL_PATTERN } from "@/lib/useRequiredFields";
import { publicApi } from "./api";

export function PortalForgotForm() {
  const messageId = useId();
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState<string>();
  const [sent, setSent] = useState(false);
  const [pending, setPending] = useState(false);

  const submit = async () => {
    if (!email.trim()) return setMessage("Informe o e-mail.");
    if (!EMAIL_PATTERN.test(email.trim())) return setMessage("Informe um e-mail válido.");
    setMessage(undefined);
    setPending(true);
    try {
      await publicApi.portalForgotPassword(email.trim());
      setSent(true);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Não foi possível enviar. Tente novamente.");
    } finally {
      setPending(false);
    }
  };

  return (
    <AuthLayout title="Esqueci minha senha" subtitle="Enviamos um link para você criar uma nova senha.">
      {sent ? (
        <Alert tone="success" title="Confira seu e-mail">
          Se este e-mail tiver acesso ao portal, você vai receber o link em alguns minutos. Ele vale por 1 hora.
        </Alert>
      ) : (
        <Form
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
          noValidate
        >
          <TextField
            label="E-mail"
            type="email"
            autoComplete="username"
            placeholder="E-mail"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            {...(message ? { "aria-invalid": true, "aria-describedby": messageId } : {})}
          />
          {message && (
            <ErrorMessage id={messageId} role="alert">
              {message}
            </ErrorMessage>
          )}
          <Button type="submit" loading={pending} fullWidth>
            Enviar link
          </Button>
        </Form>
      )}
      <FormFooter>
        <TextLink href="/portal/entrar">Voltar para o login</TextLink>
      </FormFooter>
    </AuthLayout>
  );
}
