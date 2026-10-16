// SystemHealthPage - System health metrics page

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../lib/api';
import type { SystemHealthResponse } from '../../types/metrics';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { StatusBadge } from '../../components/common/StatusBadge';
import { formatBytes, formatPercent } from '../../lib/formatters';
import { HEALTH_POLL_INTERVAL_MS } from '../../lib/constants';
import { Server, Database, Cpu, MemoryStick } from 'lucide-react';
import { AdminLayout } from '../../components/admin/AdminLayout';

export function SystemHealthPage() {
  const { data: health, isLoading, error, refetch } = useQuery<SystemHealthResponse, Error>({
    queryKey: ['admin', 'health'],
    queryFn: () => apiClient.getSystemHealth(),
    refetchInterval: HEALTH_POLL_INTERVAL_MS,
    staleTime: HEALTH_POLL_INTERVAL_MS,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });

  if (isLoading) {
    return (
      <AdminLayout>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
          <LoadingSpinner size="lg" ariaLabel="Loading system health" />
          <p className="text-slate-600 dark:text-slate-400 text-sm">Loading system health...</p>
        </div>
      </AdminLayout>
    );
  }

  if (error) {
    return (
      <AdminLayout>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
          <StatusBadge status="FAILED" label="Failed to load system health" />
          <p className="text-slate-600 dark:text-slate-400 text-sm max-w-xs">{error.message}</p>
          <button
            onClick={() => refetch()}
            className="mt-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors"
          >
            Retry
          </button>
        </div>
      </AdminLayout>
    );
  }

  return (
    <AdminLayout>
      <div className="space-y-8">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-slate-900 dark:text-white mb-2">System Health</h1>
            <p className="text-slate-600 dark:text-slate-400">Application and infrastructure health metrics</p>
          </div>
          <StatusBadge
            status="UNKNOWN"
            label="Health checks pending"
          />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          {health && (
            <>
              <div className="bg-slate-100/50 dark:bg-slate-800/50 border border-slate-300/50 dark:border-slate-600/50 rounded-xl p-5">
                <div className="flex items-center gap-3 mb-2">
                  <Cpu className="w-6 h-6 text-indigo-700 dark:text-indigo-400" aria-hidden="true" />
                  <h3 className="text-lg font-semibold text-slate-900 dark:text-white">CPU Usage</h3>
                </div>
                <p className="font-mono text-3xl text-slate-900 dark:text-white">{health.cpuUsage.toFixed(1)}%</p>
                <p className="text-sm text-slate-600 dark:text-slate-400 mt-1">
                  {health.cpuUsage > 80 ? 'High' : health.cpuUsage > 50 ? 'Moderate' : 'Normal'}
                </p>
              </div>

              <div className="bg-slate-100/50 dark:bg-slate-800/50 border border-slate-300/50 dark:border-slate-600/50 rounded-xl p-5">
                <div className="flex items-center gap-3 mb-2">
                  <MemoryStick className="w-6 h-6 text-indigo-700 dark:text-indigo-400" aria-hidden="true" />
                  <h3 className="text-lg font-semibold text-slate-900 dark:text-white">Heap Memory</h3>
                </div>
                <p className="font-mono text-3xl text-slate-900 dark:text-white">
                  {formatBytes(health.heapUsedBytes)} / {formatBytes(health.heapMaxBytes)}
                </p>
                <p className="text-sm text-slate-600 dark:text-slate-400 mt-1">
                  {formatPercent((health.heapUsedBytes / health.heapMaxBytes) * 100)} used
                </p>
              </div>

              <div className="bg-slate-100/50 dark:bg-slate-800/50 border border-slate-300/50 dark:border-slate-600/50 rounded-xl p-5">
                <div className="flex items-center gap-3 mb-2">
                  <Server className="w-6 h-6 text-indigo-700 dark:text-indigo-400" aria-hidden="true" />
                  <h3 className="text-lg font-semibold text-slate-900 dark:text-white">Live Threads</h3>
                </div>
                <p className="font-mono text-3xl text-slate-900 dark:text-white">{health.liveThreads.toLocaleString()}</p>
                <p className="text-sm text-slate-600 dark:text-slate-400 mt-1">Active JVM threads</p>
              </div>

              <div className="bg-slate-100/50 dark:bg-slate-800/50 border border-slate-300/50 dark:border-slate-600/50 rounded-xl p-5">
                <div className="flex items-center gap-3 mb-2">
                  <Database className="w-6 h-6 text-indigo-700 dark:text-indigo-400" aria-hidden="true" />
                  <h3 className="text-lg font-semibold text-slate-900 dark:text-white">DB Connections</h3>
                </div>
                <p className="font-mono text-3xl text-slate-900 dark:text-white">
                  Not instrumented
                </p>
                <p className="text-sm text-slate-600 dark:text-slate-400 mt-1">Connection pool metrics are not yet wired</p>
              </div>
            </>
          )}
        </div>

        {health?.circuitBreakers && health.circuitBreakers.length > 0 && (
          <div className="bg-slate-100/50 dark:bg-slate-800/50 border border-slate-300/50 dark:border-slate-600/50 rounded-xl p-5">
            <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-4">Circuit Breakers</h3>
            <div className="space-y-3">
              {health.circuitBreakers.map((cb) => (
                <div
                  key={cb.name}
                  className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-3 bg-slate-50/50 dark:bg-slate-900/50 rounded-lg border border-slate-300/50 dark:border-slate-700/50"
                >
                  <div className="flex items-center gap-3">
                    <StatusBadge status={cb.state} label={cb.name} />
                    <div className="text-sm text-slate-600 dark:text-slate-400">
                      Failure Rate: <span className="font-mono text-slate-900 dark:text-white">{formatPercent(cb.failureRate)}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-4 text-sm text-slate-600 dark:text-slate-400">
                    <span>Failed: <span className="font-mono text-red-700 dark:text-red-400">{cb.failedCalls}</span></span>
                    <span>Success: <span className="font-mono text-green-700 dark:text-green-400">{cb.successfulCalls}</span></span>
                    <span>Buffered: <span className="font-mono text-slate-900 dark:text-white">{cb.bufferedCalls}</span></span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <details className="bg-slate-100/50 dark:bg-slate-800/50 border border-slate-300/50 dark:border-slate-600/50 rounded-xl p-5">
          <summary className="font-mono text-sm text-slate-600 dark:text-slate-400 cursor-pointer">
            View Raw Health Data
          </summary>
          <pre className="mt-4 p-4 bg-slate-50/50 dark:bg-slate-900/50 rounded-lg text-xs text-slate-700 dark:text-slate-300 overflow-auto">
            {JSON.stringify(health, null, 2)}
          </pre>
        </details>
      </div>
    </AdminLayout>
  );
}
