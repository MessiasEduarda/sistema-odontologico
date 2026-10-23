"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import styled from "styled-components";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Card, CardTitle, DetailList, PageHeader, Row, Stack } from "@/components/ui/display";
import { TextField } from "@/components/ui/TextField";
import { useMe } from "@/features/auth/hooks";
import { passwordProblem } from "@/features/auth/passwordSchema";
import { errorMessage } from "@/lib/forms";
import { api } from "@/lib/api/endpoints";
import type { SessionInfo } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { useAction } from "@/lib/mutations";

export function ProfilePage() {
  const { data: me } = useMe();
  return (
    <Stack>
      <PageHeader title="Meu perfil" />
      <Card>
        <CardTitle>Dados da conta</CardTitle>
        <DetailList>
          <div>
            <dt>Nome</dt>
            <dd>{me?.user.name}</dd>
          </div>
          <div>
            <dt>E-mail</dt>
            <dd>{me?.user.email}</dd>
          </div>
          <div>
            <dt>Clínica ativa</dt>
            <dd>{me?.activeClinic?.name ?? "—"}</dd>
          </div>
          <div>
            <dt>Clínicas com acesso</dt>
            <dd>{me?.clinics.map((c) => c.name).join(", ") || "—"}</dd>
          </div>
          {me?.platformAdmin && (
            <div>
              <dt>Plataforma</dt>
              <dd>
                <Badge $tone="primary">Administrador da plataforma</Badge>
              </dd>
            </div>
          )}
        </DetailList>
      </Card>
      <PasswordCard />
      <SessionsCard />
    </Stack>
  );
}

function PasswordCard() {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [message, setMessage] = useState<string>();
  const change = useAction(() => api.account.changePassword(current, next), {
    success: "Senha alterada com sucesso! As outras sessões foram encerradas.",
    invalidate: [["sessions"]],
    onSuccess: () => {
      setCurrent("");
      setNext("");
      setConfirm("");
    },
  });
  const submit = () => {
    const problem = (!current && "Informe a senha atual.") || passwordProblem(next, confirm);
    setMessage(problem || undefined);
    if (!problem) change.mutate(undefined);
  };
  const shown = message ?? (change.error ? errorMessage(change.error) : undefined);
  return (
    <Card>
      <CardTitle>Trocar senha</CardTitle>
      <PasswordFields>
        <TextField
          label="Senha atual"
          type="password"
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
        />
        <TextField
          label="Nova senha"
          type="password"
          autoComplete="new-password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
        />
        <TextField
          label="Confirme a nova senha"
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
        />
      </PasswordFields>
      {shown && <FormMessage role="alert">{shown}</FormMessage>}
      <Row style={{ marginTop: shown ? 8 : 20 }}>
        <Button onClick={submit} loading={change.isPending}>
          Trocar senha
        </Button>
      </Row>
    </Card>
  );
}

const FormMessage = styled.p`
  margin-top: ${({ theme }) => theme.space[4]};
  color: ${({ theme }) => theme.colors.danger};
  font-size: ${({ theme }) => theme.fontSizes.md};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
`;

function SessionsCard() {
  const sessions = useQuery({ queryKey: ["sessions"], queryFn: api.account.sessions });
  const revoke = useAction(api.account.revokeSession, {
    success: "Sessão encerrada com sucesso!",
    invalidate: [["sessions"]],
  });
  const revokeOthers = useAction(api.account.revokeOthers, {
    success: "Outras sessões encerradas com sucesso!",
    invalidate: [["sessions"]],
  });
  return (
    <Card $padding={false}>
      <div style={{ padding: "20px 24px 0" }}>
        <CardTitle>
          Sessões ativas
          <Button variant="outline" onClick={() => revokeOthers.mutate(undefined)} loading={revokeOthers.isPending}>
            Sair dos outros dispositivos
          </Button>
        </CardTitle>
      </div>
      <DataTable<SessionInfo>
        caption="Sessões ativas"
        rows={sessions.data}
        loading={sessions.isPending}
        rowKey={(s) => s.id}
        columns={[
          { key: "device", header: "Navegador", render: (s) => describeAgent(s.userAgent) },
          { key: "ip", header: "IP", render: (s) => s.ip ?? "—" },
          { key: "since", header: "Desde", hideOnMobile: true, render: (s) => formatDateTime(s.createdAt) },
          { key: "last", header: "Último uso", render: (s) => formatDateTime(s.lastSeenAt) },
          {
            key: "actions",
            header: "",
            render: (s) =>
              s.current ? (
                <Badge $tone="success">Esta sessão</Badge>
              ) : (
                <Button variant="ghost" onClick={() => revoke.mutate(s.id)}>
                  Encerrar
                </Button>
              ),
          },
        ]}
      />
    </Card>
  );
}

function describeAgent(ua: string | null): string {
  if (!ua) return "Desconhecido";
  const browser = /Edg\//.test(ua)
    ? "Edge"
    : /Chrome\//.test(ua)
      ? "Chrome"
      : /Firefox\//.test(ua)
        ? "Firefox"
        : /Safari\//.test(ua)
          ? "Safari"
          : "Navegador";
  const os = /Windows/.test(ua)
    ? "Windows"
    : /Android/.test(ua)
      ? "Android"
      : /iPhone|iPad/.test(ua)
        ? "iOS"
        : /Mac OS/.test(ua)
          ? "macOS"
          : /Linux/.test(ua)
            ? "Linux"
            : "";
  return os ? `${browser} · ${os}` : browser;
}

const PasswordFields = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[4]};
  width: 100%;
  max-width: 460px;
`;
