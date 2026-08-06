// useMetrics - TanStack Query wrappers for admin metrics endpoints

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../lib/api';
import type {
  MetricsOverviewResponse,
  CacheMetricsResponse,
  RedisMetricsResponse,
  CircuitBreakerMetricsResponse,
} from '../types/metrics';
import { METRICS_POLL_INTERVAL_MS } from '../lib/constants';

/**
 * Custom hook for polling overview metrics from /api/admin/metrics/overview
 * Polls every 5 seconds as specified in FRONTEND-INTEGRATION-GUIDE.md
 */
export function useOverviewMetrics() {
  return useQuery<MetricsOverviewResponse, Error>({
    queryKey: ['admin', 'metrics', 'overview'],
    queryFn: () => apiClient.getOverviewMetrics(),
    refetchInterval: METRICS_POLL_INTERVAL_MS,
    staleTime: METRICS_POLL_INTERVAL_MS,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });
}

/**
 * Custom hook for polling cache metrics from /api/admin/metrics/cache
 * Polls every 5 seconds as specified in FRONTEND-INTEGRATION-GUIDE.md
 */
export function useCacheMetrics() {
  return useQuery<CacheMetricsResponse, Error>({
    queryKey: ['admin', 'metrics', 'cache'],
    queryFn: () => apiClient.getCacheMetrics(),
    refetchInterval: METRICS_POLL_INTERVAL_MS,
    staleTime: METRICS_POLL_INTERVAL_MS,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });
}

/**
 * Custom hook for polling Redis metrics from /api/admin/metrics/redis
 * Polls every 5 seconds as specified in FRONTEND-INTEGRATION-GUIDE.md
 */
export function useRedisMetrics() {
  return useQuery<RedisMetricsResponse, Error>({
    queryKey: ['admin', 'metrics', 'redis'],
    queryFn: () => apiClient.getRedisMetrics(),
    refetchInterval: METRICS_POLL_INTERVAL_MS,
    staleTime: METRICS_POLL_INTERVAL_MS,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });
}

/**
 * Custom hook for polling circuit breaker metrics from /api/admin/metrics/circuit-breakers
 * Polls every 5 seconds as specified in FRONTEND-INTEGRATION-GUIDE.md
 */
export function useCircuitBreakers() {
  return useQuery<CircuitBreakerMetricsResponse[], Error>({
    queryKey: ['admin', 'metrics', 'circuit-breakers'],
    queryFn: () => apiClient.getCircuitBreakerMetrics(),
    refetchInterval: METRICS_POLL_INTERVAL_MS,
    staleTime: METRICS_POLL_INTERVAL_MS,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });
}

/**
 * Custom hook for polling system health from /api/admin/health
 * Polls every 10 seconds as specified in FRONTEND-INTEGRATION-GUIDE.md
 */
export function useSystemHealth() {
  const { HEALTH_POLL_INTERVAL_MS } = require('../lib/constants');

  return useQuery<import('../types/metrics').SystemHealthResponse, Error>({
    queryKey: ['admin', 'health'],
    queryFn: () => apiClient.getSystemHealth(),
    refetchInterval: HEALTH_POLL_INTERVAL_MS,
    staleTime: HEALTH_POLL_INTERVAL_MS,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });
}