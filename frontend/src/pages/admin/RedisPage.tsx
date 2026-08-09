// RedisPage - Redis metrics page

import { useRedisMetrics } from '../../hooks/useMetrics';
import { MetricCard } from '../../components/common/MetricCard';
import { formatBytes, formatCount } from '../../lib/formatters';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { StatusBadge } from '../../components/common/StatusBadge';
import { AdminLayout } from '../../components/admin/AdminLayout';

export function RedisPage() {
  const { data: redis, isLoading, error } = useRedisMetrics();

  if (isLoading) {
    return (
      <AdminLayout>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
          <LoadingSpinner size="lg" ariaLabel="Loading Redis metrics" />
          <p className="text-slate-400 text-sm">Loading Redis metrics...</p>
        </div>
      </AdminLayout>
    );
  }

  if (error) {
    return (
      <AdminLayout>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
          <StatusBadge status="FAILED" label="Failed to load Redis metrics" />
          <p className="text-slate-400 text-sm max-w-xs">{error.message}</p>
        </div>
      </AdminLayout>
    );
  }

  return (
    <AdminLayout>
      <div className="space-y-8">
        <div>
          <h1 className="text-3xl font-bold text-white mb-2">Redis Metrics</h1>
          <p className="text-slate-400">Redis connection and memory statistics</p>
        </div>

        {/* Connection Status */}
        <div className="mb-6 flex items-center gap-4">
          <StatusBadge
            status={redis?.connected ? 'CONNECTED' : 'DISCONNECTED'}
            label="Redis Connection"
          />
        </div>

        {/* Metrics Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          {redis && (
            <>
              <MetricCard
                label="Memory Used"
                value={formatBytes(redis.memoryUsedBytes)}
                unit="bytes"
              />
              <MetricCard
                label="Total Keys"
                value={formatCount(redis.totalKeys)}
                unit="keys"
              />
              <MetricCard
                label="Hit Ratio"
                value={redis.hitRatio.toFixed(1)}
                unit="%"
              />
              <MetricCard
                label="Active Locks"
                value={redis.activeLocks.toLocaleString()}
                unit="locks"
              />
            </>
          )}
        </div>

        {/* Additional Details */}
        <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5">
          <h3 className="text-lg font-semibold text-white mb-4">Details</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <p className="text-slate-400 text-sm">Connected Clients</p>
              <p className="font-mono text-2xl text-white">{redis?.connectedClients ?? '—'}</p>
            </div>
            <div>
              <p className="text-slate-400 text-sm">Active Seat Locks</p>
              <p className="font-mono text-2xl text-white">{redis?.activeLocks ?? '—'}</p>
            </div>
          </div>
        </div>
      </div>
    </AdminLayout>
  );
}