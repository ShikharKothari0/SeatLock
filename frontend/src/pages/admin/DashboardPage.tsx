// DashboardPage - Admin overview dashboard

import { useOverviewMetrics, useCircuitBreakers } from '../../hooks/useMetrics';
import { MetricCard } from '../../components/common/MetricCard';
import { formatMs, formatPercent } from '../../lib/formatters';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { StatusBadge } from '../../components/common/StatusBadge';
import { AdminLayout } from '../../components/admin/AdminLayout';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useRef, useEffect, useState } from 'react';

export function DashboardPage() {
  const { data: overview, isLoading: overviewLoading, error: overviewError } = useOverviewMetrics();
  const { data: circuitBreakers, isLoading: cbLoading } = useCircuitBreakers();

  const [trendData, setTrendData] = useState<Array<{ time: string; bookings: number }>>([]);
  const chartRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (overview) {
      setTrendData((prev) => {
        const newPoint = {
          time: new Date().toLocaleTimeString(),
          bookings: overview.totalBookings,
        };
        const updated = [...prev, newPoint].slice(-60);
        return updated;
      });
    }
  }, [overview]);

  if (overviewLoading || cbLoading) {
    return (
      <AdminLayout>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
          <LoadingSpinner size="lg" ariaLabel="Loading dashboard" />
          <p className="text-slate-400 text-sm">Loading dashboard...</p>
        </div>
      </AdminLayout>
    );
  }

  if (overviewError) {
    return (
      <AdminLayout>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
          <StatusBadge status="FAILED" label="Failed to load dashboard" />
          <p className="text-slate-400 text-sm max-w-xs">
            {overviewError.message}
          </p>
        </div>
      </AdminLayout>
    );
  }

  return (
    <AdminLayout>
      <div className="space-y-8">
        <div>
          <h1 className="text-3xl font-bold text-white mb-2">Admin Dashboard</h1>
          <p className="text-slate-400">System overview and key metrics</p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
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

        <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5">
          <h3 className="text-lg font-semibold text-white mb-4">Booking Trend (Live)</h3>
          <div className="h-64" ref={chartRef}>
            {trendData.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={trendData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorBookings" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#6366f1" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="#6366f1" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#334155" vertical={false} />
                  <XAxis
                    dataKey="time"
                    stroke="#64748b"
                    fontSize={11}
                    tick={{ fill: '#94a3b8' }}
                    interval="preserveStartEnd"
                    tickFormatter={(value: string) => value}
                  />
                  <YAxis
                    stroke="#64748b"
                    fontSize={11}
                    tick={{ fill: '#94a3b8' }}
                    tickFormatter={(value: number) => value.toLocaleString()}
                  />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: '#1e293b',
                      border: '1px solid #334155',
                      borderRadius: '8px',
                      boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
                    }}
                    labelStyle={{ color: '#f8fafc' }}
                    formatter={(value: number) => [value.toLocaleString(), 'Bookings']}
                  />
                  <Area
                    type="monotone"
                    dataKey="bookings"
                    stroke="#6366f1"
                    strokeWidth={2}
                    fillOpacity={1}
                    fill="url(#colorBookings)"
                  />
                </AreaChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex items-center justify-center h-full text-slate-500">
                Collecting data...
              </div>
            )}
          </div>
        </div>

        <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5">
          <h3 className="text-lg font-semibold text-white mb-4">Circuit Breaker Status</h3>
          <div className="flex flex-wrap gap-4">
            {circuitBreakers?.map((cb) => (
              <div
                key={cb.name}
                className="flex items-center gap-3 px-4 py-3 bg-slate-900/50 rounded-lg border border-slate-700/50 min-w-[240px]"
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
      </div>
    </AdminLayout>
  );
}