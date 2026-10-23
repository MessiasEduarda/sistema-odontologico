"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { AuthLayout, Form, FormFooter } from "@/features/auth/AuthLayout";
import { ErrorMessage, TextLink } from "@/features/auth/LoginForm";
import { EMAIL_PATTERN } from "@/lib/useRequiredFields";
import { publicApi } from "./api";

export const PORTAL_ME_KEY = ["portal-me"];

export function PortalLoginForm() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const messageId = useId();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [challenge, setChallenge] = useState<{ id: string; maskedEmail: string } | null>(null);
  const [message, setMessage] = useState<string>();
  const [pending, setPending] = useState(false);

  const enter = () => {
    queryClient.clear();
    router.replace("/portal");
  };

  const submitCredentials = async () => {
    if (!email.trim() && !password) return setMessage("Informe o e-mail e a senha.");
    if (!EMAIL_PATTERN.test(email.trim())) return setMessage("Informe um e-mail válido.");
    if (!password) return setMessage("Informe sua senha.");
    setMessage(undefined);
    setPending(true);
    try {
      const result = await publicApi.portalLogin(email.trim(), password);
      if (result.status === "AUTHENTICATED") return enter();
      setChallenge({ id: result.challengeId ?? "", maskedEmail: result.maskedEmail ?? email });
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Não foi possível entrar. Tente novamente.");
    } finally {
      setPending(false);
    }
  };

  const submitCode = async () => {
    if (!/^[0-9]{6}$/.test(code.trim())) return setMessage("Informe o código de 6 números.");
    setMessage(undefined);
    setPending(true);
    try {
      await publicApi.portalVerifyCode(challenge?.id ?? "", code.trim());
      enter();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Não foi possível confirmar o código.");
    } finally {
      setPending(false);
    }
  };

  const invalid = message ? { "aria-invalid": true, "aria-describedby": messageId } : {};

  return (
    <AuthLayout
      title="Portal do paciente"
      subtitle={
        challenge
          ? `Enviamos um código de 6 números para ${challenge.maskedEmail}. Ele vale por 10 minutos.`
          : "Entre com o e-mail cadastrado na clínica e a sua senha."
      }
    >
      {challenge ? (
        <Form
          onSubmit={(e) => {
            e.preventDefault();
            void submitCode();
          }}
          noValidate
        >
          <TextField
            label="Código de verificação"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            placeholder="000000"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
            {...invalid}
          />
          {message && (
            <ErrorMessage id={messageId} role="alert">
              {message}
            </ErrorMessage>
          )}
          <Button type="submit" loading={pending} fullWidth>
            Confirmar
          </Button>
          <FormFooter>
            <TextLink
              href="/portal/entrar"
              onClick={() => {
                setChallenge(null);
                setCode("");
                setMessage(undefined);
              }}
            >
              Voltar
            </TextLink>
          </FormFooter>
        </Form>
      ) : (
        <Form
          onSubmit={(e) => {
            e.preventDefault();
            void submitCredentials();
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
            {...invalid}
          />
          <TextField
            label="Senha"
            type="password"
            autoComplete="current-password"
            placeholder="Senha"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            {...invalid}
          />
          {message && (
            <ErrorMessage id={messageId} role="alert">
              {message}
            </ErrorMessage>
          )}
          <Button type="submit" loading={pending} fullWidth>
            Acessar
          </Button>
          <FormFooter>
            <TextLink href="/portal/esqueci-senha">Esqueci minha senha</TextLink>
          </FormFooter>
        </Form>
      )}
    </AuthLayout>
  );
}
