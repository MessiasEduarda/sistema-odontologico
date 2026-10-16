"use client";

import { useMutation, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { useToast } from "@/components/ui/Toast";
import { useMe } from "@/features/auth/hooks";

/**
 * Mutação padrão: mostra notificação de sucesso/erro e invalida as consultas afetadas.
 * O erro também fica em `mutation.error` para formulários exibirem nos campos.
 */
export function useAction<TVars, TResult>(
  fn: (vars: TVars) => Promise<TResult>,
  options: {
    success?: string | ((result: TResult) => string);
    invalidate?: QueryKey[];
    onSuccess?: (result: TResult, vars: TVars) => void;
  } = {},
) {
  const toast = useToast();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: async (result, vars) => {
      await Promise.all((options.invalidate ?? []).map((key) => queryClient.invalidateQueries({ queryKey: key })));
      if (options.success) {
        toast.success(typeof options.success === "function" ? options.success(result) : options.success);
      }
      options.onSuccess?.(result, vars);
    },
    onError: (error: Error) => toast.error(error.message),
  });
}

/** Verifica permissão na clínica ativa (só para exibir/ocultar; a API autoriza de verdade). */
export function useCan() {
  const { data: me } = useMe();
  return (...permissions: string[]) => permissions.some((p) => me?.permissions.includes(p));
}
