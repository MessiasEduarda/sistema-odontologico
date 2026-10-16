"use client";

import { useSyncExternalStore } from "react";
import { tokenFromHash } from "./forms";

function subscribe(onChange: () => void) {
  window.addEventListener("hashchange", onChange);
  return () => window.removeEventListener("hashchange", onChange);
}

/**
 * Token do fragmento da URL (#token=...). `null` durante a renderização no servidor (ainda desconhecido),
 * string vazia quando a URL não tem token.
 */
export function useHashToken(): string | null {
  return useSyncExternalStore(
    subscribe,
    () => tokenFromHash() ?? "",
    () => null,
  );
}
