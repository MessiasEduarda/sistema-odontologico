"use client";

import { useQuery } from "@tanstack/react-query";
import { useMe } from "@/features/auth/hooks";
import { api } from "@/lib/api/endpoints";
import { useHydrated } from "@/lib/useHydrated";

const REFRESH = 60_000;

export function useClinicSupportUnread() {
  const { data: me } = useMe();
  const hydrated = useHydrated();
  const query = useQuery({
    queryKey: ["support-unread"],
    queryFn: api.support.unread,
    enabled: Boolean(me?.activeClinic),
    refetchInterval: REFRESH,
  });
  return hydrated ? (query.data?.count ?? 0) : 0;
}

export function usePlatformSupportUnread() {
  const { data: me } = useMe();
  const hydrated = useHydrated();
  const query = useQuery({
    queryKey: ["platform-support-unread"],
    queryFn: api.platform.supportUnread,
    enabled: Boolean(me?.platformAdmin),
    refetchInterval: REFRESH,
  });
  return hydrated ? (query.data?.count ?? 0) : 0;
}
