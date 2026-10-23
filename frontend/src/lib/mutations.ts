"use client";

import { useMutation, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { useFeedback } from "@/components/ui/Feedback";
import { useToast } from "@/components/ui/Toast";
import { useMe } from "@/features/auth/hooks";
import { ApiError } from "@/lib/api/client";

export function useAction<TVars, TResult>(
  fn: (vars: TVars) => Promise<TResult>,
  options: {
    success?: string | ((result: TResult) => string);
    invalidate?: QueryKey[];
    onSuccess?: (result: TResult, vars: TVars) => void;
  } = {},
) {
  const toast = useToast();
  const feedback = useFeedback();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: async (result, vars) => {
      await Promise.all((options.invalidate ?? []).map((key) => queryClient.invalidateQueries({ queryKey: key })));
      if (options.success) {
        feedback.success(typeof options.success === "function" ? options.success(result) : options.success);
      }
      options.onSuccess?.(result, vars);
    },
    onError: (error: Error) => {
      if (isRefusal(error)) feedback.error("Não foi possível concluir a ação", error.message);
      else toast.error(error.message);
    },
  });
}

function isRefusal(error: Error): boolean {
  return error instanceof ApiError && error.status !== 401 && error.fieldErrors.length === 0;
}

export function useCan() {
  const { data: me } = useMe();
  return (...permissions: string[]) => permissions.some((p) => me?.permissions.includes(p));
}
