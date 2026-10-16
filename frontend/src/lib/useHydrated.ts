"use client";

import { useSyncExternalStore } from "react";

const noop = () => () => {};

/** `false` no servidor e durante a hidratação; `true` depois. Evita divergência com dados já em cache no cliente. */
export function useHydrated(): boolean {
  return useSyncExternalStore(
    noop,
    () => true,
    () => false,
  );
}
