"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import styled from "styled-components";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { useMe } from "./hooks";

const CLINIC_PICKER = "/selecionar-clinica";

/**
 * Navegação da área autenticada (a proteção real é a autorização da API, em toda requisição):
 * sem sessão → login; sessão sem clínica ativa (com clínicas disponíveis) → seleção de clínica.
 * As páginas sempre são renderizadas e exibem o próprio estado de carregamento enquanto a sessão é consultada
 * (compatível com a navegação instantânea do Next.js); nenhum dado é exibido sem resposta autenticada da API.
 */
export function SessionGuard({ children }: { children: ReactNode }) {
  const { data: me, isPending, isError, error, refetch } = useMe();
  const router = useRouter();
  const pathname = usePathname();

  const needsLogin = !isPending && !isError && me === null;
  const needsClinic = Boolean(me && !me.activeClinic && me.clinics.length > 0 && pathname !== CLINIC_PICKER);

  useEffect(() => {
    if (needsLogin) router.replace(`/entrar?next=${encodeURIComponent(pathname)}`);
    else if (needsClinic) router.replace(CLINIC_PICKER);
  }, [needsLogin, needsClinic, pathname, router]);

  if (isError) {
    return (
      <Centered>
        <Alert tone="danger" title="Não foi possível carregar sua sessão">
          {error.message}
        </Alert>
        <Button variant="outline" onClick={() => refetch()}>
          Tentar novamente
        </Button>
      </Centered>
    );
  }
  return <>{children}</>;
}

const Centered = styled.div`
  min-height: 100vh;
  display: grid;
  place-content: center;
  gap: ${({ theme }) => theme.space[4]};
  padding: ${({ theme }) => theme.space[4]};
`;
