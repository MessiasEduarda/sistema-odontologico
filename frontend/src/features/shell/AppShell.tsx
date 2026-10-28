"use client";

import { LifeBuoy, LogOut, Menu, PanelLeftClose, PanelLeftOpen, Repeat, ShieldCheck, UserCog, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import styled, { css } from "styled-components";
import { useQuery } from "@tanstack/react-query";
import { useLogout, useMe } from "@/features/auth/hooks";
import { ToothMark } from "@/components/domain/ToothMark";
import { api } from "@/lib/api/endpoints";
import { UnreadBadge, unreadLabel } from "@/components/ui/UnreadBadge";
import { useClinicSupportUnread, usePlatformSupportUnread } from "@/features/support/useSupportUnread";
import { usePersistentFlag } from "@/lib/usePersistentFlag";
import { visibleNav, type NavItem } from "./navigation";

export function AppShell({ children }: { children: ReactNode }) {
  const { data: me } = useMe();
  const logout = useLogout();
  const pathname = usePathname();
  const [collapsed, setCollapsed] = usePersistentFlag("odonto.sidebar.collapsed", false);
  const [mobileOpen, setMobileOpen] = useState(false);

  const settings = useQuery({
    queryKey: ["clinic-settings"],
    queryFn: api.clinic.settings,
    enabled: Boolean(me?.activeClinic),
    staleTime: 300_000,
  });
  const unreadCount = useClinicSupportUnread();
  const platformUnread = usePlatformSupportUnread();
  const totalUnread = unreadCount + platformUnread;
  const items = visibleNav(me?.permissions ?? [], { aesthetics: settings.data?.aestheticsEnabled ?? false });
  const isActive = (item: NavItem) => pathname === item.href || pathname.startsWith(`${item.href}/`);

  const navLinks = (compact: boolean) => (
    <NavList>
      {items.map((item) => (
        <li key={item.href}>
          <NavLink
            href={item.href}
            aria-current={isActive(item) ? "page" : undefined}
            title={compact ? item.label : undefined}
            aria-label={compact ? item.label : undefined}
            $compact={compact}
            onClick={() => setMobileOpen(false)}
          >
            <item.icon size={20} strokeWidth={1.7} aria-hidden="true" />
            {!compact && <span>{item.label}</span>}
          </NavLink>
        </li>
      ))}
      {me?.platformAdmin && (
        <li>
          <NavLink
            href="/plataforma"
            aria-current={pathname.startsWith("/plataforma") ? "page" : undefined}
            title={compact ? "Plataforma" : undefined}
            aria-label={
              platformUnread > 0 ? `Plataforma, ${unreadLabel(platformUnread)}` : compact ? "Plataforma" : undefined
            }
            $compact={compact}
            onClick={() => setMobileOpen(false)}
          >
            <IconWithBadge>
              <ShieldCheck size={20} strokeWidth={1.7} aria-hidden="true" />
              {compact && <UnreadBadge count={platformUnread} floating />}
            </IconWithBadge>
            {!compact && <span>Plataforma</span>}
            {!compact && <EndBadge count={platformUnread} />}
          </NavLink>
        </li>
      )}
    </NavList>
  );

  const footer = (compact: boolean) => (
    <Footer>
      {me?.activeClinic && (
        <NavLink
          href="/suporte"
          aria-current={pathname.startsWith("/suporte") ? "page" : undefined}
          title={compact ? "Suporte" : undefined}
          aria-label={
            unreadCount > 0 ? `Suporte, ${unreadLabel(unreadCount)}` : compact ? "Suporte" : undefined
          }
          $compact={compact}
          onClick={() => setMobileOpen(false)}
        >
          <IconWithBadge>
            <LifeBuoy size={20} strokeWidth={1.7} aria-hidden="true" />
            {compact && <UnreadBadge count={unreadCount} floating />}
          </IconWithBadge>
          {!compact && <span>Suporte</span>}
          {!compact && <EndBadge count={unreadCount} />}
        </NavLink>
      )}
      <NavLink
        href="/perfil"
        aria-current={pathname.startsWith("/perfil") ? "page" : undefined}
        title={compact ? "Meu perfil" : undefined}
        aria-label={compact ? "Meu perfil" : undefined}
        $compact={compact}
        onClick={() => setMobileOpen(false)}
      >
        <UserCog size={20} strokeWidth={1.7} aria-hidden="true" />
        {!compact && <span>Meu perfil</span>}
      </NavLink>
      <NavButton
        type="button"
        onClick={() => logout.mutate()}
        title={compact ? "Sair da conta" : undefined}
        aria-label={compact ? "Sair da conta" : undefined}
        $compact={compact}
      >
        <LogOut size={20} strokeWidth={1.7} aria-hidden="true" />
        {!compact && <span>Sair da conta</span>}
      </NavButton>
    </Footer>
  );

  const clinicName = me?.activeClinic?.name ?? "Odonto SaaS";
  const header = (compact: boolean) =>
    compact ? (
      <CompactBrand>
        <ToothMark size={40} />
      </CompactBrand>
    ) : (
      <ClinicHeader>
        <Brand>
          <ToothMark size={44} />
          <BrandText>
            <ClinicName title={clinicName}>{clinicName}</ClinicName>
            <Greeting>Olá, {me?.user.name.split(" ")[0] ?? ""}</Greeting>
          </BrandText>
        </Brand>
        {(me?.clinics.length ?? 0) > 1 && (
          <SwitchLink href="/selecionar-clinica">
            <Repeat size={14} aria-hidden="true" /> Trocar de clínica
          </SwitchLink>
        )}
      </ClinicHeader>
    );

  return (
    <Layout $collapsed={collapsed}>
      <SkipLink href="#conteudo">Pular para o conteúdo</SkipLink>

      <Sidebar $collapsed={collapsed} aria-label="Menu principal">
        <Toggle
          type="button"
          onClick={() => setCollapsed(!collapsed)}
          aria-label={collapsed ? "Expandir menu" : "Recolher menu"}
          aria-expanded={!collapsed}
          title={collapsed ? "Expandir menu" : "Recolher menu"}
        >
          {collapsed ? <PanelLeftOpen size={20} aria-hidden /> : <PanelLeftClose size={20} aria-hidden />}
        </Toggle>
        {header(collapsed)}
        <Divider />
        <nav aria-label="Seções">{navLinks(collapsed)}</nav>
        {footer(collapsed)}
      </Sidebar>

      <MobileBar>
        <Toggle
          type="button"
          onClick={() => setMobileOpen(true)}
          aria-label={totalUnread > 0 ? `Abrir menu, ${unreadLabel(totalUnread)}` : "Abrir menu"}
          aria-expanded={mobileOpen}
        >
          <IconWithBadge>
            <Menu size={22} aria-hidden />
            <UnreadBadge count={totalUnread} floating />
          </IconWithBadge>
        </Toggle>
        <MobileTitle>{me?.activeClinic?.name ?? "Odonto SaaS"}</MobileTitle>
      </MobileBar>
      {mobileOpen && <Backdrop onClick={() => setMobileOpen(false)} aria-hidden="true" />}
      <Drawer $open={mobileOpen} aria-label="Menu principal" aria-hidden={!mobileOpen} inert={!mobileOpen}>
        <Toggle type="button" onClick={() => setMobileOpen(false)} aria-label="Fechar menu">
          <X size={22} aria-hidden />
        </Toggle>
        {header(false)}
        <Divider />
        <nav aria-label="Seções">{navLinks(false)}</nav>
        {footer(false)}
      </Drawer>

      <Content id="conteudo" tabIndex={-1}>
        {children}
      </Content>
    </Layout>
  );
}

const desktop = (s: ReturnType<typeof css>) => css`
  @media (min-width: ${({ theme }) => theme.breakpoints.lg}px) {
    ${s}
  }
`;

const Layout = styled.div<{ $collapsed: boolean }>`
  min-height: 100vh;
  ${({ $collapsed, theme }) =>
    desktop(css`
      padding-left: ${$collapsed ? theme.layout.sidebarCollapsed : theme.layout.sidebarExpanded};
      transition: padding-left ${theme.transitions.base};
    `)}
`;

const sidebarBase = css`
  background: ${({ theme }) => theme.colors.sidebar};
  color: ${({ theme }) => theme.colors.sidebarText};
  display: flex;
  flex-direction: column;
  padding: ${({ theme }) => `${theme.space[3]} ${theme.space[2]}`};
  overflow-y: auto;
  overflow-x: hidden;
`;

const Sidebar = styled.aside<{ $collapsed: boolean }>`
  ${sidebarBase}
  display: none;
  position: fixed;
  inset: 0 auto 0 0;
  z-index: ${({ theme }) => theme.zIndex.sidebar};
  width: ${({ theme, $collapsed }) => ($collapsed ? theme.layout.sidebarCollapsed : theme.layout.sidebarExpanded)};
  transition: width ${({ theme }) => theme.transitions.base};
  align-items: ${({ $collapsed }) => ($collapsed ? "center" : "stretch")};

  @media (min-width: ${({ theme }) => theme.breakpoints.lg}px) {
    display: flex;
  }
`;

const Drawer = styled.aside<{ $open: boolean }>`
  ${sidebarBase}
  position: fixed;
  inset: 0 auto 0 0;
  width: min(280px, 85vw);
  z-index: ${({ theme }) => theme.zIndex.modal};
  transform: translateX(${({ $open }) => ($open ? "0" : "-100%")});
  transition: transform ${({ theme }) => theme.transitions.base};

  @media (min-width: ${({ theme }) => theme.breakpoints.lg}px) {
    display: none;
  }
`;

const Backdrop = styled.div`
  position: fixed;
  inset: 0;
  background: ${({ theme }) => theme.colors.overlay};
  z-index: ${({ theme }) => theme.zIndex.modal - 1};

  @media (min-width: ${({ theme }) => theme.breakpoints.lg}px) {
    display: none;
  }
`;

const MobileBar = styled.header`
  position: sticky;
  top: 0;
  z-index: ${({ theme }) => theme.zIndex.sidebar};
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space[3]};
  padding: ${({ theme }) => `${theme.space[2]} ${theme.space[3]}`};
  background: ${({ theme }) => theme.colors.sidebar};
  color: ${({ theme }) => theme.colors.sidebarText};

  @media (min-width: ${({ theme }) => theme.breakpoints.lg}px) {
    display: none;
  }
`;

const MobileTitle = styled.span`
  font-family: ${({ theme }) => theme.fonts.display};
  font-weight: ${({ theme }) => theme.fontWeights.semibold};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const Toggle = styled.button`
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: ${({ theme }) => theme.radii.md};
  background: transparent;
  color: inherit;
  cursor: pointer;
  flex-shrink: 0;

  &:hover {
    background: ${({ theme }) => theme.colors.sidebarHover};
  }
  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.colors.sidebarText};
    outline-offset: 2px;
  }
`;

const CompactBrand = styled.div`
  display: grid;
  place-items: center;
  margin: ${({ theme }) => theme.space[3]} 0 ${({ theme }) => theme.space[1]};
`;

const ClinicHeader = styled.div`
  padding: ${({ theme }) => `${theme.space[3]} ${theme.space[2]} ${theme.space[2]}`};
  display: grid;
  gap: ${({ theme }) => theme.space[2]};
`;

const Brand = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${({ theme }) => theme.space[3]};
  min-width: 0;
`;

const BrandText = styled.div`
  display: grid;
  gap: 2px;
  min-width: 0;
`;

const ClinicName = styled.p`
  font-family: ${({ theme }) => theme.fonts.display};
  font-size: 0.9375rem;
  font-weight: ${({ theme }) => theme.fontWeights.bold};
  line-height: 1.2;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  overflow-wrap: anywhere;
`;

const Greeting = styled.p`
  font-size: ${({ theme }) => theme.fontSizes.sm};
  color: ${({ theme }) => theme.colors.sidebarTextMuted};
`;

const SwitchLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: ${({ theme }) => theme.space[1]};
  font-size: ${({ theme }) => theme.fontSizes.xs};
  color: ${({ theme }) => theme.colors.sidebarTextMuted};
  text-decoration: none;

  &:hover {
    color: ${({ theme }) => theme.colors.sidebarText};
  }
`;

const Divider = styled.hr`
  width: 100%;
  border: none;
  border-top: 1px solid ${({ theme }) => theme.colors.sidebarHover};
  margin: ${({ theme }) => theme.space[2]} 0;
`;

const NavList = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 2px;
`;

const itemStyles = css<{ $compact: boolean }>`
  display: flex;
  align-items: center;
  justify-content: ${({ $compact }) => ($compact ? "center" : "flex-start")};
  gap: ${({ theme }) => theme.space[3]};
  width: ${({ $compact }) => ($compact ? "44px" : "100%")};
  min-height: 40px;
  padding: ${({ theme, $compact }) => ($compact ? "0" : `0 ${theme.space[3]}`)};
  border-radius: ${({ theme }) => theme.radii.md};
  color: ${({ theme }) => theme.colors.sidebarText};
  text-decoration: none;
  font-size: ${({ theme }) => theme.fontSizes.md};
  border: none;
  background: transparent;
  cursor: pointer;
  white-space: nowrap;

  &:hover {
    background: ${({ theme }) => theme.colors.sidebarHover};
  }
  &[aria-current="page"] {
    background: ${({ theme }) => theme.colors.sidebarActive};
    color: #fff;
    box-shadow: inset 3px 0 0 ${({ theme }) => theme.colors.primary};
  }
  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.colors.sidebarText};
    outline-offset: 1px;
  }
`;

const NavLink = styled(Link)<{ $compact: boolean }>`
  ${itemStyles}
`;

const IconWithBadge = styled.span`
  position: relative;
  display: inline-flex;
`;

const EndBadge = styled(UnreadBadge)`
  margin-left: auto;
`;

const NavButton = styled.button<{ $compact: boolean }>`
  ${itemStyles}
`;

const Footer = styled.div`
  margin-top: auto;
  padding-top: ${({ theme }) => theme.space[3]};
  border-top: 1px solid ${({ theme }) => theme.colors.sidebarHover};
  display: grid;
  gap: 2px;
  justify-items: inherit;
`;

const Content = styled.main`
  padding: ${({ theme }) => `${theme.space[6]} ${theme.space[4]}`};
  max-width: ${({ theme }) => theme.layout.contentMaxWidth};
  margin: 0 auto;
  outline: none;

  @media (min-width: ${({ theme }) => theme.breakpoints.md}px) {
    padding: ${({ theme }) => `${theme.space[8]} ${theme.space[8]}`};
  }
`;

const SkipLink = styled.a`
  position: absolute;
  left: -9999px;
  top: 8px;
  z-index: 1000;
  padding: 8px 16px;
  background: ${({ theme }) => theme.colors.surface};
  border-radius: ${({ theme }) => theme.radii.md};

  &:focus {
    left: 8px;
  }
`;
