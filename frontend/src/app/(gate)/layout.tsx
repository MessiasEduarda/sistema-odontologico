import type { ReactNode } from "react";
import { SessionGuard } from "@/features/auth/SessionGuard";

/** Telas autenticadas sem o menu lateral (ex.: escolha de clínica). */
export default function GateLayout({ children }: { children: ReactNode }) {
  return <SessionGuard>{children}</SessionGuard>;
}
