"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { ApiError } from "@/lib/api/client";
import { useHydrated } from "@/lib/useHydrated";
import { authApi, type Me } from "./api";

export const ME_KEY = ["auth", "me"] as const;

/**
 * Usuário autenticado. `data` é null quando não há sessão (401). Até a hidratação terminar, a consulta aparece
 * como pendente: o HTML do servidor não tem o usuário e precisa coincidir com a primeira renderização do cliente.
 */
export function useMe() {
  const hydrated = useHydrated();
  const query = useQuery<Me | null>({
    queryKey: ME_KEY,
    queryFn: async () => {
      try {
        return await authApi.me();
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null;
        throw error;
      }
    },
    staleTime: 60_000,
  });
  if (hydrated) return query;
  return { ...query, data: undefined, isPending: true, isSuccess: false, status: "pending" as const };
}

export function hasPermission(me: Me | null | undefined, permission: string): boolean {
  return Boolean(me?.permissions.includes(permission));
}

export function useSwitchClinic() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: authApi.switchClinic,
    onSuccess: (me) => {
      // Dados em cache pertencem à clínica anterior: descarta tudo antes de exibir a nova.
      queryClient.clear();
      queryClient.setQueryData(ME_KEY, me);
    },
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      queryClient.clear();
      router.replace("/entrar");
    },
  });
}
