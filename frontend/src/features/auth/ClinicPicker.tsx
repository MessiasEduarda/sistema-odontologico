"use client";

import { LogOut } from "lucide-react";
import { useRouter } from "next/navigation";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { LoadingState } from "@/components/ui/LoadingState";
import { AuthLayout } from "./AuthLayout";
import { useLogout, useMe, useSwitchClinic } from "./hooks";

export function ClinicPicker() {
  const { data: me } = useMe();
  const switchClinic = useSwitchClinic();
  const logout = useLogout();
  const router = useRouter();

  if (!me) return <LoadingState />;

  const choose = (clinicId: string) => switchClinic.mutate(clinicId, { onSuccess: () => router.replace("/inicio") });

  return (
    <AuthLayout title="Escolha a clínica" subtitle={`Olá, ${me.user.name}. Em qual clínica você vai trabalhar agora?`}>
      {switchClinic.isError && <Alert tone="danger">{switchClinic.error.message}</Alert>}
      {me.clinics.length === 0 ? (
        <Alert tone="info">Você ainda não tem acesso a nenhuma clínica. Peça um convite ao responsável.</Alert>
      ) : (
        <List>
          {me.clinics.map((clinic) => (
            <li key={clinic.id}>
              <ClinicButton
                type="button"
                onClick={() => choose(clinic.id)}
                disabled={switchClinic.isPending}
                aria-current={me.activeClinic?.id === clinic.id ? "true" : undefined}
              >
                <span>{clinic.name}</span>
                <Arrow aria-hidden="true">→</Arrow>
              </ClinicButton>
            </li>
          ))}
        </List>
      )}
      <Button variant="ghost" onClick={() => logout.mutate()} loading={logout.isPending}>
        <LogOut size={18} aria-hidden /> Sair da conta
      </Button>
    </AuthLayout>
  );
}

const List = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: ${({ theme }) => theme.space[3]};
`;

const ClinicButton = styled.button`
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 56px;
  padding: ${({ theme }) => `${theme.space[3]} ${theme.space[5]}`};
  border-radius: ${({ theme }) => theme.radii.md};
  border: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.surface};
  color: ${({ theme }) => theme.colors.text};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  text-align: left;
  cursor: pointer;
  transition:
    border-color ${({ theme }) => theme.transitions.fast},
    background ${({ theme }) => theme.transitions.fast};

  &:hover:not(:disabled),
  &[aria-current="true"] {
    border-color: ${({ theme }) => theme.colors.primary};
    background: ${({ theme }) => theme.colors.primarySoft};
  }
  &:focus-visible {
    outline: none;
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }
  &:disabled {
    opacity: 0.6;
    cursor: progress;
  }
`;

const Arrow = styled.span`
  color: ${({ theme }) => theme.colors.primaryText};
`;
