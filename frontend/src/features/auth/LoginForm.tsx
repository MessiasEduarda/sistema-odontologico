"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import styled from "styled-components";
import { z } from "zod";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { applyServerErrors, safeRedirectPath } from "@/lib/forms";
import { authApi } from "./api";
import { AuthLayout, Form, FormFooter } from "./AuthLayout";
import { ME_KEY } from "./hooks";

const schema = z.object({
  email: z.email({ error: "Informe um e-mail válido." }),
  password: z.string().min(1, { error: "Informe sua senha." }),
});
type Values = z.infer<typeof schema>;

export function LoginForm() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string>();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async ({ email, password }) => {
    setFormError(undefined);
    try {
      const me = await authApi.login(email, password);
      queryClient.clear();
      queryClient.setQueryData(ME_KEY, me);
      const next = safeRedirectPath(new URLSearchParams(window.location.search).get("next"));
      router.replace(me.activeClinic || me.clinics.length === 0 ? next : "/selecionar-clinica");
    } catch (error) {
      setFormError(applyServerErrors(error, setError, ["email", "password"]));
    }
  });

  return (
    <AuthLayout title="Entrar" subtitle="Acesse com o e-mail e a senha cadastrados.">
      <Form onSubmit={onSubmit} noValidate>
        {formError && <Alert tone="danger">{formError}</Alert>}
        <TextField
          label="E-mail"
          type="email"
          autoComplete="username"
          placeholder="voce@clinica.com.br"
          error={errors.email?.message}
          {...register("email")}
        />
        <TextField
          label="Senha"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register("password")}
        />
        <Button type="submit" loading={isSubmitting} fullWidth>
          Entrar
        </Button>
        <FormFooter>
          <TextLink href="/esqueci-senha">Esqueci minha senha</TextLink>
        </FormFooter>
      </Form>
    </AuthLayout>
  );
}

export const TextLink = styled(Link)`
  color: ${({ theme }) => theme.colors.primaryText};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
`;
