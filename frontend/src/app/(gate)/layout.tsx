import type { ReactNode } from "react";
import { SessionGuard } from "@/features/auth/SessionGuard";

export default function GateLayout({ children }: { children: ReactNode }) {
  return <SessionGuard>{children}</SessionGuard>;
}
