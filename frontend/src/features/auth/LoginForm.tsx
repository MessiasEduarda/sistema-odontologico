"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { useForm, type FieldErrors } from "react-hook-form";
import styled from "styled-components";
import { z } from "zod";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { api } from "@/lib/api/endpoints";
import { safeRedirectPath } from "@/lib/forms";
import { authApi } from "./api";
import { AuthLayout, Form, FormFooter } from "./AuthLayout";
import { ME_KEY } from "./hooks";

const schema = z.object({
  email: z.email(),
  password: z.string().min(1),
});
type Values = z.infer<typeof schema>;

function validationMessage(errors: FieldErrors<Values>): string {
  if (errors.email && errors.password) return "Informe o e-mail e a senha.";
  if (errors.email) return "Informe um e-mail válido.";
  return "Informe sua senha.";
}

export function LoginForm() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const messageId = useId();
  const [message, setMessage] = useState<string>();
  const contact = useQuery({
    queryKey: ["support-contact"],
    queryFn: api.support.contact,
    staleTime: 3_600_000,
    retry: false,
  });
  const {
    register,
    handleSubmit,
    formState: { isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(
    async ({ email, password }) => {
      setMessage(undefined);
      try {
        const me = await authApi.login(email, password);
        queryClient.clear();
        queryClient.setQueryData(ME_KEY, me);
        const next = safeRedirectPath(new URLSearchParams(window.location.search).get("next"));
        router.replace(me.activeClinic || me.clinics.length === 0 ? next : "/selecionar-clinica");
      } catch (error) {
        setMessage(error instanceof Error ? error.message : "Não foi possível entrar. Tente novamente.");
      }
    },
    (errors) => setMessage(validationMessage(errors)),
  );

  const invalid = message ? { "aria-invalid": true, "aria-describedby": messageId } : {};

  return (
    <AuthLayout title="Entrar" hideTitle>
      <Form onSubmit={onSubmit} noValidate>
        <TextField
          label="E-mail"
          type="email"
          autoComplete="username"
          placeholder="E-mail"
          {...invalid}
          {...register("email")}
        />
        <TextField
          label="Senha"
          type="password"
          autoComplete="current-password"
          placeholder="Senha"
          {...invalid}
          {...register("password")}
        />
        {message && (
          <ErrorMessage id={messageId} role="alert">
            {message}
          </ErrorMessage>
        )}
        <Button type="submit" loading={isSubmitting} fullWidth>
          Acessar
        </Button>
        <FormFooter>
          <TextLink href="/esqueci-senha">Esqueci minha senha</TextLink>
          {contact.data?.email && (
            <SupportLink href={`mailto:${contact.data.email}`} title={contact.data.email}>
              Falar com o suporte
            </SupportLink>
          )}
        </FormFooter>
      </Form>
    </AuthLayout>
  );
}

const SupportLink = styled.a`
  color: ${({ theme }) => theme.colors.primaryText};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  text-decoration: none;
`;

export const ErrorMessage = styled.p`
  margin: ${({ theme }) => theme.space[2]} 0 -10px;
  color: #ffd9d2;
  font-size: ${({ theme }) => theme.fontSizes.md};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  text-align: left;
`;

export const TextLink = styled(Link)`
  color: ${({ theme }) => theme.colors.primaryText};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  text-decoration: none;
`;
