"use client";

import { useQuery } from "@tanstack/react-query";
import { UserPlus } from "lucide-react";
import { useState } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { SearchInput, useRowSearch } from "@/components/ui/SearchInput";
import { Badge, FilterBar, Muted, PageHeader, Row, Stack } from "@/components/ui/display";
import { CheckboxField, FieldError, FormGrid, SelectField } from "@/components/ui/fields";
import { ConfirmDialog, Modal } from "@/components/ui/Modal";
import { Tabs } from "@/components/ui/Tabs";
import { TextField } from "@/components/ui/TextField";
import { useMe } from "@/features/auth/hooks";
import { api } from "@/lib/api/endpoints";
import type { AuditEvent, Invitation, Member, RoleInfo } from "@/lib/api/types";
import { formatDateTime, toDateInput } from "@/lib/format";
import { ROLE_LABELS } from "@/lib/labels";
import { useAction, useCan } from "@/lib/mutations";
import { P } from "@/lib/permissions";
import { EMAIL_PATTERN, useRequiredFields } from "@/lib/useRequiredFields";

const PERMISSION_LABELS: Record<string, string> = {
  "clinic:settings:manage": "Configurações e cadastros",
  "users:read": "Ver equipe",
  "users:manage": "Gerenciar equipe",
  "audit:read": "Auditoria",
  "patients:read": "Ver pacientes",
  "patients:write": "Editar pacientes",
  "schedule:read": "Ver agenda",
  "schedule:write": "Agendar",
  "clinical_records:read": "Ver prontuário",
  "clinical_records:write": "Registrar no prontuário",
  "treatments:read": "Ver orçamentos",
  "treatments:write": "Editar orçamentos",
  "finance:read": "Ver financeiro",
  "finance:write": "Movimentar financeiro",
  "inventory:read": "Ver estoque",
  "inventory:write": "Movimentar estoque",
  "reports:read": "Relatórios",
};

export function TeamPage() {
  const can = useCan();
  const [tab, setTab] = useState("membros");
  const items = [
    { id: "membros", label: "Membros", content: <MembersTab /> },
    can(P.USERS_MANAGE) && { id: "convites", label: "Convites pendentes", content: <InvitationsTab /> },
    { id: "papeis", label: "Papéis e permissões", content: <RolesTab /> },
    can(P.AUDIT_READ) && { id: "auditoria", label: "Auditoria", content: <AuditTab /> },
  ].filter(Boolean) as { id: string; label: string; content: React.ReactNode }[];
  return (
    <Stack>
      <PageHeader title="Equipe" />
      <Tabs label="Equipe" active={tab} onChange={setTab} items={items} />
    </Stack>
  );
}

function MembersTab() {
  const can = useCan();
  const { data: me } = useMe();
  const [inviting, setInviting] = useState(false);
  const [editing, setEditing] = useState<Member | null>(null);
  const [deactivating, setDeactivating] = useState<Member | null>(null);
  const members = useQuery({ queryKey: ["members"], queryFn: api.clinic.members });
  const search = useRowSearch(members.data, (m) => [m.name, m.email]);
  const deactivate = useAction(() => api.clinic.deactivateMember(deactivating?.membershipId ?? ""), {
    success: "Acesso desativado com sucesso!",
    invalidate: [["members"]],
    onSuccess: () => setDeactivating(null),
  });
  const manage = can(P.USERS_MANAGE);

  return (
    <Stack $gap={4}>
      <Row $justify="space-between">
        <SearchInput
          label="Buscar por nome"
          placeholder="Nome ou e-mail…"
          value={search.query}
          onChange={search.setQuery}
        />
        {manage && (
          <Button onClick={() => setInviting(true)}>
            <UserPlus size={16} aria-hidden /> Convidar pessoa
          </Button>
        )}
      </Row>
      <DataTable<Member>
        caption="Membros da equipe"
        rows={search.rows}
        loading={members.isPending}
        error={members.error}
        onRetry={members.refetch}
        rowKey={(m) => m.membershipId}
        columns={[
          {
            key: "name",
            header: "Nome",
            render: (m) => (
              <span>
                <strong>{m.name}</strong> {m.userId === me?.user.id && <Muted>(você)</Muted>}
              </span>
            ),
          },
          { key: "email", header: "E-mail", hideOnMobile: true, render: (m) => m.email },
          {
            key: "roles",
            header: "Papéis",
            render: (m) => (
              <Row $gap={1}>
                {m.roles.map((r) => (
                  <Badge key={r} $tone="primary">
                    {ROLE_LABELS[r] ?? r}
                  </Badge>
                ))}
              </Row>
            ),
          },
          {
            key: "status",
            header: "Situação",
            render: (m) => (m.status === "ACTIVE" ? <Badge $tone="success">Ativo</Badge> : <Badge>Desativado</Badge>),
          },
          {
            key: "actions",
            header: "",
            render: (m) =>
              manage &&
              m.status === "ACTIVE" && (
                <Row $gap={2} $wrap={false}>
                  <Button variant="ghost" onClick={() => setEditing(m)}>
                    Papéis
                  </Button>
                  {m.userId !== me?.user.id && (
                    <Button variant="ghost" onClick={() => setDeactivating(m)}>
                      Desativar
                    </Button>
                  )}
                </Row>
              ),
          },
        ]}
      />
      {inviting && <InviteDialog onClose={() => setInviting(false)} />}
      {editing && <RolesDialog member={editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog
        open={Boolean(deactivating)}
        question={`Tem certeza que deseja desativar o acesso de ${deactivating?.name}?`}
        message={`${deactivating?.name} perderá o acesso a esta clínica imediatamente (as sessões abertas são encerradas). O histórico de ações é mantido.`}
        confirmLabel="Desativar"
        tone="danger"
        loading={deactivate.isPending}
        onConfirm={() => deactivate.mutate(undefined)}
        onClose={() => setDeactivating(null)}
      />
    </Stack>
  );
}

function InviteDialog({ onClose }: { onClose: () => void }) {
  const roles = useQuery({ queryKey: ["roles"], queryFn: api.clinic.roles });
  const [email, setEmail] = useState("");
  const [role, setRole] = useState("DENTIST");
  const form = useRequiredFields({
    email: (!email.trim() && "Informe o e-mail.") || (!EMAIL_PATTERN.test(email.trim()) && "Informe um e-mail válido."),
  });
  const invite = useAction(() => api.clinic.invite(email, role), {
    success: "Convite enviado por e-mail com sucesso!",
    invalidate: [["invitations"]],
    onSuccess: onClose,
  });
  return (
    <Modal
      open
      onClose={onClose}
      title="Convidar para a equipe"
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => form.check() && invite.mutate(undefined)} loading={invite.isPending}>
            Enviar convite
          </Button>
        </>
      }
    >
      <FormGrid $columns={1}>
        <TextField
          label="E-mail"
          type="email"
          required
          error={form.errors.email}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <SelectField
          label="Papel"
          value={role}
          onChange={(e) => setRole(e.target.value)}
          options={(roles.data ?? []).map((r) => ({ value: r.code, label: ROLE_LABELS[r.code] ?? r.name }))}
        />
        <Muted>
          A pessoa recebe um link válido por 7 dias para criar a senha (ou confirmar com a conta que já tem).
        </Muted>
        {invite.error && <Alert tone="danger">{invite.error.message}</Alert>}
      </FormGrid>
    </Modal>
  );
}

function RolesDialog({ member, onClose }: { member: Member; onClose: () => void }) {
  const roles = useQuery({ queryKey: ["roles"], queryFn: api.clinic.roles });
  const [selected, setSelected] = useState<string[]>(member.roles);
  const save = useAction(() => api.clinic.updateMemberRoles(member.membershipId, selected), {
    success: "Papéis atualizados com sucesso!",
    invalidate: [["members"]],
    onSuccess: onClose,
  });
  const toggle = (code: string) => setSelected((s) => (s.includes(code) ? s.filter((c) => c !== code) : [...s, code]));
  const form = useRequiredFields({ roles: selected.length === 0 && "Selecione pelo menos um papel." });
  return (
    <Modal
      open
      onClose={onClose}
      title={`Papéis de ${member.name}`}
      footer={
        <>
          <Button data-modal-cancel variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => form.check() && save.mutate(undefined)} loading={save.isPending}>
            Salvar
          </Button>
        </>
      }
    >
      <Stack $gap={3}>
        {(roles.data ?? []).map((r) => (
          <CheckboxField
            key={r.code}
            label={ROLE_LABELS[r.code] ?? r.name}
            checked={selected.includes(r.code)}
            onChange={() => toggle(r.code)}
          />
        ))}
        {form.errors.roles && <FieldError>{form.errors.roles}</FieldError>}
        <Muted>
          As permissões somam-se quando a pessoa tem mais de um papel. As mudanças valem na próxima ação dela.
        </Muted>
        {save.error && <Alert tone="danger">{save.error.message}</Alert>}
      </Stack>
    </Modal>
  );
}

function InvitationsTab() {
  const invitations = useQuery({ queryKey: ["invitations"], queryFn: api.clinic.invitations });
  const resend = useAction((i: Invitation) => api.clinic.invite(i.email, i.roleCode), {
    success: "Convite reenviado com sucesso! O link anterior deixou de valer.",
    invalidate: [["invitations"]],
  });
  const revoke = useAction(api.clinic.revokeInvitation, {
    success: "Convite revogado com sucesso!",
    invalidate: [["invitations"]],
  });
  return (
    <DataTable<Invitation>
      caption="Convites pendentes"
      rows={invitations.data}
      loading={invitations.isPending}
      rowKey={(i) => i.id}
      emptyTitle="Nenhum convite pendente"
      columns={[
        { key: "email", header: "E-mail", render: (i) => i.email },
        { key: "role", header: "Papel", render: (i) => ROLE_LABELS[i.roleCode] ?? i.roleCode },
        { key: "sent", header: "Enviado em", render: (i) => formatDateTime(i.createdAt) },
        { key: "exp", header: "Expira em", render: (i) => formatDateTime(i.expiresAt) },
        {
          key: "actions",
          header: "",
          render: (i) => (
            <Row $gap={2} $wrap={false}>
              <Button variant="ghost" onClick={() => resend.mutate(i)} loading={resend.isPending}>
                Reenviar
              </Button>
              <Button variant="ghost" onClick={() => revoke.mutate(i.id)}>
                Revogar
              </Button>
            </Row>
          ),
        },
      ]}
    />
  );
}

function RolesTab() {
  const roles = useQuery({ queryKey: ["roles"], queryFn: api.clinic.roles });
  const list = roles.data ?? [];
  const permissions = Object.keys(PERMISSION_LABELS);
  return (
    <Stack $gap={3}>
      <Muted>Matriz de permissões dos papéis da clínica.</Muted>
      <MatrixWrap>
        <Matrix>
          <caption className="sr-only">Permissões por papel</caption>
          <thead>
            <tr>
              <th scope="col">Permissão</th>
              {list.map((r: RoleInfo) => (
                <th key={r.code} scope="col">
                  {ROLE_LABELS[r.code] ?? r.name}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {permissions.map((p) => (
              <tr key={p}>
                <th scope="row">{PERMISSION_LABELS[p]}</th>
                {list.map((r) => (
                  <td key={r.code}>
                    {r.permissions.includes(p) ? <span aria-label="sim">✓</span> : <span aria-label="não">—</span>}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </Matrix>
      </MatrixWrap>
    </Stack>
  );
}

const ACTION_LABELS: Record<string, string> = {
  LOGIN_SUCCEEDED: "Login",
  LOGOUT: "Saída",
  TENANT_SWITCHED: "Entrou na clínica",
  INVITATION_CREATED: "Convite enviado",
  INVITATION_ACCEPTED: "Convite aceito",
  INVITATION_REVOKED: "Convite revogado",
  MEMBER_ROLES_CHANGED: "Papéis alterados",
  MEMBER_DEACTIVATED: "Membro desativado",
  RECORD_CREATED: "Criação",
  RECORD_UPDATED: "Alteração",
  RECORD_DEACTIVATED: "Desativação",
  RECORD_CANCELLED: "Cancelamento",
  RECORD_VOIDED: "Anulação",
  CLINICAL_RECORD_VIEWED: "Prontuário acessado",
  ATTACHMENT_DOWNLOADED: "Anexo baixado",
  REPORT_EXPORTED: "Relatório exportado",
  CONSENT_SIGNED: "Termo assinado",
  CONSENT_REVOKED: "Termo revogado",
  PORTAL_ACCESS_GRANTED: "Acesso ao portal gerado",
  PORTAL_ACCESSED: "Portal acessado pelo paciente",
  PAYMENT_RECEIVED: "Recebimento",
  PAYMENT_REFUNDED: "Estorno",
};

function AuditTab() {
  const today = new Date();
  const [from, setFrom] = useState(toDateInput(new Date(today.getTime() - 7 * 86400000)));
  const [to, setTo] = useState(toDateInput(today));
  const [action, setAction] = useState("");
  const events = useQuery({
    queryKey: ["audit", from, to, action],
    queryFn: () => api.clinic.audit({ from, to, action: action || undefined }),
  });
  const search = useRowSearch(events.data, (e) => [e.actorName, e.actorEmail]);
  return (
    <Stack $gap={4}>
      <FilterBar>
        <TextField label="De" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        <TextField label="Até" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        <ActionFilter>
          <SelectField
            label="Ação"
            value={action}
            onChange={(e) => setAction(e.target.value)}
            placeholder="Todas"
            options={Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label }))}
          />
        </ActionFilter>
        <SearchInput label="Buscar por nome" placeholder="Quem fez…" value={search.query} onChange={search.setQuery} />
      </FilterBar>
      <DataTable<AuditEvent>
        caption="Eventos de auditoria"
        rows={search.rows}
        loading={events.isPending}
        error={events.error}
        onRetry={events.refetch}
        rowKey={(e) => e.id}
        emptyTitle="Nenhum evento no período"
        columns={[
          { key: "when", header: "Quando", minWidth: "150px", render: (e) => formatDateTime(e.occurredAt) },
          { key: "who", header: "Quem", render: (e) => e.actorName ?? "Paciente / sistema" },
          { key: "action", header: "Ação", render: (e) => ACTION_LABELS[e.action] ?? e.action },
          { key: "resource", header: "Registro", hideOnMobile: true, render: (e) => e.resourceType ?? "—" },
          { key: "ip", header: "IP", hideOnMobile: true, render: (e) => e.ip ?? "—" },
        ]}
      />
    </Stack>
  );
}

const MatrixWrap = styled.div`
  overflow-x: auto;
  background: ${({ theme }) => theme.colors.surface};
  border-radius: ${({ theme }) => theme.radii.lg};
  box-shadow: ${({ theme }) => theme.shadows.card};
`;

const Matrix = styled.table`
  border-collapse: collapse;
  width: 100%;
  font-size: ${({ theme }) => theme.fontSizes.sm};

  th,
  td {
    padding: ${({ theme }) => `${theme.space[2]} ${theme.space[3]}`};
    border-bottom: 1px solid ${({ theme }) => theme.colors.border};
    text-align: center;
  }
  thead th {
    background: ${({ theme }) => theme.colors.tableHeaderEnd};
    color: #fff;
    white-space: nowrap;
  }
  tbody th {
    text-align: left;
    font-weight: ${({ theme }) => theme.fontWeights.medium};
  }
  td span[aria-label="sim"] {
    color: ${({ theme }) => theme.colors.success};
    font-weight: bold;
  }
`;

const ActionFilter = styled.div`
  width: 240px;
`;
