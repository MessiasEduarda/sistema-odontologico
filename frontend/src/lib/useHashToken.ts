"use client";

import { useSyncExternalStore } from "react";
import { tokenFromHash } from "./forms";

function subscribe(onChange: () => void) {
  window.addEventListener("hashchange", onChange);
  return () => window.removeEventListener("hashchange", onChange);
}

export function useHashToken(): string | null {
  return useSyncExternalStore(
    subscribe,
    () => tokenFromHash() ?? "",
    () => null,
  );
}
