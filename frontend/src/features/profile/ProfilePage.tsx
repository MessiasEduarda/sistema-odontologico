"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { Badge, Card, CardTitle, DetailList, Muted, PageHeader, Row, Stack } from "@/components/ui/display";
import { TextField } from "@/components/ui/TextField";
import { useMe } from "@/features/auth/hooks";
import { PASSWORD_HINT } from "@/features/auth/passwordSchema";
import { ApiError } from "@/lib/api/client";
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
  const change = useAction(() => api.account.changePassword(current, next), {
    success: "Senha alterada. As outras sessões foram encerradas.",
    invalidate: [["sessions"]],
    onSuccess: () => {
      setCurrent("");
      setNext("");
      setConfirm("");
    },
  });
  const field = (name: string) =>
    change.error instanceof ApiError ? change.error.fieldErrors.find((f) => f.field === name)?.message : undefined;
  const mismatch = confirm.length > 0 && confirm !== next;
  return (
    <Card>
      <CardTitle>Trocar senha</CardTitle>
      <Muted style={{ marginBottom: 16 }}>{PASSWORD_HINT}</Muted>
      <PasswordFields>
        <TextField
          label="Senha atual"
          type="password"
          autoComplete="current-password"
          value={current}
          error={field("currentPassword")}
          onChange={(e) => setCurrent(e.target.value)}
        />
        <TextField
          label="Nova senha"
          type="password"
          autoComplete="new-password"
          value={next}
          error={field("newPassword")}
          onChange={(e) => setNext(e.target.value)}
        />
        <TextField
          label="Confirme a nova senha"
          type="password"
          autoComplete="new-password"
          value={confirm}
          error={mismatch ? "As senhas não coincidem." : undefined}
          onChange={(e) => setConfirm(e.target.value)}
        />
      </PasswordFields>
      {change.error && !(change.error instanceof ApiError && change.error.fieldErrors.length > 0) && (
        <Alert tone="danger">{change.error.message}</Alert>
      )}
      <Row style={{ marginTop: 20 }}>
        <Button
          onClick={() => change.mutate(undefined)}
          loading={change.isPending}
          disabled={!current || next.length < 10 || mismatch || !confirm}
        >
          Trocar senha
        </Button>
      </Row>
    </Card>
  );
}

function SessionsCard() {
  const sessions = useQuery({ queryKey: ["sessions"], queryFn: api.account.sessions });
  const revoke = useAction(api.account.revokeSession, { success: "Sessão encerrada.", invalidate: [["sessions"]] });
  const revokeOthers = useAction(api.account.revokeOthers, {
    success: "Outras sessões encerradas.",
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

/** Campos de senha empilhados, todos com a mesma largura. */
const PasswordFields = styled.div`
  display: grid;
  gap: ${({ theme }) => theme.space[4]};
  width: 100%;
  max-width: 460px;
`;
