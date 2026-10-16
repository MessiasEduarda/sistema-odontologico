"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { applyServerErrors } from "@/lib/forms";
import { useHashToken } from "@/lib/useHashToken";
import { authApi } from "./api";
import { AuthLayout, Form, FormFooter } from "./AuthLayout";
import { TextLink } from "./LoginForm";
import { newPasswordSchema, PASSWORD_HINT } from "./passwordSchema";

interface Values {
  name: string;
  password: string;
}

export function AcceptInvitationForm() {
  const token = useHashToken();
  const [accepted, setAccepted] = useState(false);
  const [formError, setFormError] = useState<string>();

  const preview = useQuery({
    queryKey: ["invitation", token],
    queryFn: () => authApi.previewInvitation(token ?? ""),
    enabled: Boolean(token),
    retry: false,
  });

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>();

  const existing = preview.data?.existingAccount ?? false;

  const onSubmit = handleSubmit(async ({ name, password }) => {
    setFormError(undefined);
    if (!existing) {
      if (!name?.trim()) return setError("name", { message: "Informe seu nome." });
      const check = newPasswordSchema.safeParse(password);
      if (!check.success) return setError("password", { message: check.error.issues[0]?.message });
    }
    try {
      await authApi.acceptInvitation(token ?? "", password, existing ? undefined : name);
      setAccepted(true);
    } catch (error) {
      setFormError(applyServerErrors(error, setError, ["name", "password"]));
    }
  });

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
          <Form onSubmit={onSubmit} noValidate>
            {formError && <Alert tone="danger">{formError}</Alert>}
            <TextField label="E-mail" value={preview.data.email} disabled readOnly />
            {existing ? (
              <Alert tone="info">Este e-mail já tem uma conta. Confirme com a senha atual para aceitar.</Alert>
            ) : (
              <TextField label="Seu nome" autoComplete="name" error={errors.name?.message} {...register("name")} />
            )}
            <TextField
              label={existing ? "Senha atual" : "Crie uma senha"}
              type="password"
              autoComplete={existing ? "current-password" : "new-password"}
              hint={existing ? undefined : PASSWORD_HINT}
              error={errors.password?.message}
              {...register("password", { required: "Informe a senha." })}
            />
            <Button type="submit" loading={isSubmitting} fullWidth>
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
