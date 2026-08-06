// DashboardPage - Admin overview dashboard

import { useOverviewMetrics, useCircuitBreakers } from '../../hooks/useMetrics';
import { MetricCard } from '../../components/common/MetricCard';
import { formatMs, formatPercent } from '../../lib/formatters';
import { Navbar } from '../../components/common/Navbar';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { StatusBadge } from '../../components/common/StatusBadge';

export function DashboardPage() {
  const { data: overview, isLoading: overviewLoading, error: overviewError } = useOverviewMetrics();
  const { data: circuitBreakers, isLoading: cbLoading } = useCircuitBreakers();

  if (overviewLoading || cbLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
        <LoadingSpinner size="lg" ariaLabel="Loading dashboard" />
        <p className="text-slate-400 text-sm">Loading dashboard...</p>
      </div>
    );
  }

  if (overviewError) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
        <StatusBadge status="FAILED" label="Failed to load dashboard" />
        <p className="text-slate-400 text-sm max-w-xs">
          {overviewError.message}
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <Navbar />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-white mb-2">Admin Dashboard</h1>
          <p className="text-slate-400">System overview and key metrics</p>
        </div>

        {/* Top Row - Key Metrics */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          {overview && (
            <>
              <MetricCard
                label="Total Bookings"
                value={overview.totalBookings.toLocaleString()}
                trend={overview.bookingsPerMinute}
                unit="total"
              />
              <MetricCard
                label="Hold Success Rate"
                value={formatPercent(overview.holdSuccessRate)}
                trend={overview.holdSuccessRate > 95 ? 0.5 : overview.holdSuccessRate > 90 ? 0 : -1}
                unit="%"
              />
              <MetricCard
                label="Holds Expired"
                value={overview.holdsExpired.toLocaleString()}
                trend={overview.holdsExpired > 100 ? -1 : 0}
                unit="total"
              />
              <MetricCard
                label="Avg Hold Latency"
                value={formatMs(overview.avgHoldLatencyMs)}
                trend={overview.avgHoldLatencyMs < 200 ? 1 : overview.avgHoldLatencyMs < 500 ? 0 : -1}
                unit="ms"
              />
            </>
          )}
        </div>

        {/* Circuit Breaker Status */}
        <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5">
          <h3 className="text-lg font-semibold text-white mb-4">Circuit Breakers</h3>
          <div className="flex flex-wrap gap-4">
            {circuitBreakers?.map((cb) => (
              <div
                key={cb.name}
                className="flex items-center gap-3 px-4 py-3 bg-slate-900/50 rounded-lg border border-slate-700/50"
              >
                <StatusBadge
                  status={cb.state}
                  label={cb.name}
                />
                <div className="text-sm text-slate-400">
                  Failure Rate: <span className="font-mono text-white">{formatPercent(cb.failureRate)}</span>
                </div>
                <div className="text-sm text-slate-400">
                  Failed: <span className="font-mono text-red-400">{cb.failedCalls}</span> |
                  Success: <span className="font-mono text-green-400">{cb.successfulCalls}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </main>
    </div>
  );
}