// CachePage - Cache metrics page

import { useCacheMetrics } from '../../hooks/useMetrics';
import { MetricCard } from '../../components/common/MetricCard';
import { formatCount, formatPercent } from '../../lib/formatters';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { StatusBadge } from '../../components/common/StatusBadge';
import { AdminLayout } from '../../components/admin/AdminLayout';

export function CachePage() {
  const { data: cache, isLoading, error } = useCacheMetrics();

  if (isLoading) {
    return (
      <AdminLayout>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
          <LoadingSpinner size="lg" ariaLabel="Loading cache metrics" />
          <p className="text-slate-400 text-sm">Loading cache metrics...</p>
        </div>
      </AdminLayout>
    );
  }

  if (error) {
    return (
      <AdminLayout>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
          <StatusBadge status="FAILED" label="Failed to load cache metrics" />
          <p className="text-slate-400 text-sm max-w-xs">{error.message}</p>
        </div>
      </AdminLayout>
    );
  }

  return (
    <AdminLayout>
      <div className="space-y-8">
        <div>
          <h1 className="text-3xl font-bold text-white mb-2">Cache Metrics</h1>
          <p className="text-slate-400">Redis cache hit/miss statistics</p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          {cache && (
            <>
              <MetricCard
                label="Cache Hits"
                value={formatCount(cache.cacheHits)}
                unit="hits"
              />
              <MetricCard
                label="Cache Misses"
                value={formatCount(cache.cacheMisses)}
                unit="misses"
              />
              <MetricCard
                label="Hit Ratio"
                value={formatPercent(cache.hitRatio)}
                trend={cache.hitRatio > 90 ? 1 : cache.hitRatio > 70 ? 0 : -1}
                unit="%"
              />
              <MetricCard
                label="Active Keys"
                value={formatCount(cache.activeCacheKeys)}
                unit="keys"
              />
            </>
          )}
        </div>

        <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5">
          <h3 className="text-lg font-semibold text-white mb-4">Details</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <p className="text-slate-400 text-sm">Cache Invalidations</p>
              <p className="font-mono text-2xl text-white">{cache?.cacheInvalidations?.toLocaleString() ?? '—'}</p>
            </div>
            <div>
              <p className="text-slate-400 text-sm">Status</p>
              <p className="font-mono text-2xl text-white">
                {cache && cache.cacheHits + cache.cacheMisses > 0 ? 'Active' : 'Empty'}
              </p>
            </div>
          </div>
        </div>
      </div>
    </AdminLayout>
  );
}