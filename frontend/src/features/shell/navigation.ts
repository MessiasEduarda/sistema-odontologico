import type { LucideIcon } from "lucide-react";
import {
  BarChart3,
  Boxes,
  CalendarDays,
  ClipboardList,
  DollarSign,
  FileSignature,
  FolderCog,
  HeartPulse,
  LayoutGrid,
  MessageSquare,
  NotebookPen,
  Settings,
  Sparkles,
  Users,
  UserRound,
} from "lucide-react";
import { P, type PermissionCode } from "@/lib/permissions";

export interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  anyOf: PermissionCode[];
  requires?: "aesthetics";
}

export interface NavFlags {
  aesthetics: boolean;
}

export const CLINIC_NAV: NavItem[] = [
  { href: "/inicio", label: "Dashboard", icon: LayoutGrid, anyOf: [] },
  { href: "/agenda", label: "Agenda", icon: CalendarDays, anyOf: [P.SCHEDULE_READ] },
  { href: "/pacientes", label: "Pacientes", icon: UserRound, anyOf: [P.PATIENTS_READ] },
  { href: "/anamnese", label: "Anamnese", icon: HeartPulse, anyOf: [P.CLINICAL_READ] },
  { href: "/termos", label: "Termos", icon: FileSignature, anyOf: [P.CLINICAL_READ, P.PATIENTS_WRITE] },
  { href: "/orientacoes", label: "Orientações", icon: NotebookPen, anyOf: [P.CLINICAL_READ] },
  {
    href: "/harmonizacao",
    label: "Harmonização",
    icon: Sparkles,
    anyOf: [P.CLINICAL_READ],
    requires: "aesthetics",
  },
  { href: "/tratamentos", label: "Tratamentos", icon: ClipboardList, anyOf: [P.TREATMENTS_READ] },
  { href: "/financeiro", label: "Financeiro", icon: DollarSign, anyOf: [P.FINANCE_READ] },
  { href: "/estoque", label: "Estoque", icon: Boxes, anyOf: [P.INVENTORY_READ] },
  { href: "/comunicacao", label: "Comunicação", icon: MessageSquare, anyOf: [P.CLINIC_SETTINGS_MANAGE] },
  { href: "/relatorios", label: "Relatórios", icon: BarChart3, anyOf: [P.REPORTS_READ] },
  { href: "/equipe", label: "Equipe", icon: Users, anyOf: [P.USERS_READ] },
  { href: "/cadastros", label: "Cadastros", icon: FolderCog, anyOf: [P.CLINIC_SETTINGS_MANAGE] },
  { href: "/configuracoes", label: "Configurações", icon: Settings, anyOf: [P.CLINIC_SETTINGS_MANAGE] },
];

export function visibleNav(permissions: readonly string[], flags: NavFlags = { aesthetics: false }): NavItem[] {
  return CLINIC_NAV.filter(
    (item) =>
      (item.anyOf.length === 0 || item.anyOf.some((p) => permissions.includes(p))) &&
      (!item.requires || flags[item.requires]),
  );
}
