"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { loadDashboard } from "@/lib/api/dashboard";
import { consumeOAuthSessionFromHash, readSession } from "@/lib/session";
import { previewDashboardData } from "@/features/dashboard/data/preview-data";
import type { DashboardData } from "@/types/api";

const CACHE_TTL_MS = 30_000;
let cachedData: DashboardData | null = null;
let cachedAt = 0;
let pendingRequest: Promise<DashboardData> | null = null;

async function loadCachedDashboard(force = false) {
  if (!readSession() && !consumeOAuthSessionFromHash()) return previewDashboardData;
  if (!force && cachedData && Date.now() - cachedAt < CACHE_TTL_MS) return cachedData;
  if (!force && pendingRequest) return pendingRequest;

  pendingRequest = loadDashboard()
    .then((data) => {
      cachedData = data;
      cachedAt = Date.now();
      return data;
    })
    .finally(() => {
      pendingRequest = null;
    });
  return pendingRequest;
}

export function useDashboard() {
  const [data, setData] = useState<DashboardData | null>(cachedData);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(!cachedData);
  const mounted = useRef(true);

  const refresh = useCallback(async (force = true) => {
    setLoading(true);
    setError(null);
    try {
      const nextData = await loadCachedDashboard(force);
      if (mounted.current) setData(nextData);
    } catch (caught) {
      if (!readSession()) {
        if (mounted.current) setData(previewDashboardData);
      } else if (mounted.current) {
        setError(caught instanceof Error ? caught.message : "Unable to load the dashboard.");
      }
    } finally {
      if (mounted.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    void refresh(false);
    return () => {
      mounted.current = false;
    };
  }, [refresh]);

  return { data, error, loading, refresh };
}
