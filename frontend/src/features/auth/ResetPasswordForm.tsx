"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { applyServerErrors } from "@/lib/forms";
import { useHashToken } from "@/lib/useHashToken";
import { authApi } from "./api";
import { AuthLayout, Form, FormFooter } from "./AuthLayout";
import { TextLink } from "./LoginForm";
import { newPasswordSchema, PASSWORD_HINT } from "./passwordSchema";

const schema = z
  .object({ newPassword: newPasswordSchema, confirm: z.string() })
  .refine((v) => v.newPassword === v.confirm, { path: ["confirm"], error: "As senhas não coincidem." });
type Values = z.infer<typeof schema>;

export function ResetPasswordForm() {
  const token = useHashToken();
  const [done, setDone] = useState(false);
  const [formError, setFormError] = useState<string>();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async ({ newPassword }) => {
    setFormError(undefined);
    try {
      await authApi.resetPassword(token ?? "", newPassword);
      setDone(true);
    } catch (error) {
      setFormError(applyServerErrors(error, setError, ["newPassword"]));
    }
  });

  return (
    <AuthLayout title="Criar nova senha">
      {token === "" && <Alert tone="danger">Link inválido. Solicite uma nova redefinição de senha.</Alert>}
      {done ? (
        <Alert tone="success" title="Senha alterada">
          Por segurança, todas as sessões foram encerradas. Entre novamente com a nova senha.
        </Alert>
      ) : (
        token && (
          <Form onSubmit={onSubmit} noValidate>
            {formError && <Alert tone="danger">{formError}</Alert>}
            <TextField
              label="Nova senha"
              type="password"
              autoComplete="new-password"
              hint={PASSWORD_HINT}
              error={errors.newPassword?.message}
              {...register("newPassword")}
            />
            <TextField
              label="Confirme a nova senha"
              type="password"
              autoComplete="new-password"
              error={errors.confirm?.message}
              {...register("confirm")}
            />
            <Button type="submit" loading={isSubmitting} fullWidth>
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
