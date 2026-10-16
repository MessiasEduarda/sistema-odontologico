"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { applyServerErrors } from "@/lib/forms";
import { authApi } from "./api";
import { AuthLayout, Form, FormFooter } from "./AuthLayout";
import { TextLink } from "./LoginForm";

const schema = z.object({ email: z.email({ error: "Informe um e-mail válido." }) });
type Values = z.infer<typeof schema>;

export function ForgotPasswordForm() {
  const [sent, setSent] = useState(false);
  const [formError, setFormError] = useState<string>();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async ({ email }) => {
    setFormError(undefined);
    try {
      await authApi.forgotPassword(email);
      setSent(true);
    } catch (error) {
      setFormError(applyServerErrors(error, setError, ["email"]));
    }
  });

  return (
    <AuthLayout title="Esqueci minha senha" subtitle="Enviaremos um link para você criar uma nova senha.">
      {sent ? (
        <Alert tone="success" title="Verifique seu e-mail">
          Se houver uma conta com esse e-mail, você receberá o link em instantes. O link expira em 30 minutos.
        </Alert>
      ) : (
        <Form onSubmit={onSubmit} noValidate>
          {formError && <Alert tone="danger">{formError}</Alert>}
          <TextField
            label="E-mail"
            type="email"
            autoComplete="email"
            error={errors.email?.message}
            {...register("email")}
          />
          <Button type="submit" loading={isSubmitting} fullWidth>
            Enviar link
          </Button>
        </Form>
      )}
      <FormFooter>
        <TextLink href="/entrar">Voltar para o login</TextLink>
      </FormFooter>
    </AuthLayout>
  );
}
