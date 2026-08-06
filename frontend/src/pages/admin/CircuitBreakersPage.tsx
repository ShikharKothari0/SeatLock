// CircuitBreakersPage - Circuit breaker status page

import { useCircuitBreakers } from '../../hooks/useMetrics';
import { Navbar } from '../../components/common/Navbar';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { StatusBadge } from '../../components/common/StatusBadge';
import { formatPercent } from '../../lib/formatters';

export function CircuitBreakersPage() {
  const { data: circuitBreakers, isLoading, error } = useCircuitBreakers();

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
        <LoadingSpinner size="lg" ariaLabel="Loading circuit breaker status" />
        <p className="text-slate-400 text-sm">Loading circuit breakers...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
        <StatusBadge status="FAILED" label="Failed to load circuit breakers" />
        <p className="text-slate-400 text-sm max-w-xs">{error.message}</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <Navbar />
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-white mb-2">Circuit Breakers</h1>
          <p className="text-slate-400">Resilience4j circuit breaker status</p>
        </div>

        {circuitBreakers && circuitBreakers.length > 0 ? (
          <div className="space-y-4">
            {circuitBreakers.map((cb) => (
              <div
                key={cb.name}
                className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-5"
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4">
                  <h3 className="text-lg font-semibold text-white">{cb.name}</h3>
                  <StatusBadge status={cb.state} label={cb.state} />
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
                  <div className="p-3 bg-slate-900/50 rounded-lg">
                    <p className="text-slate-400 text-sm">State</p>
                    <p className="font-mono text-xl text-white">{cb.state}</p>
                  </div>
                  <div className="p-3 bg-slate-900/50 rounded-lg">
                    <p className="text-slate-400 text-sm">Failure Rate</p>
                    <p className="font-mono text-xl text-white">{formatPercent(cb.failureRate)}</p>
                  </div>
                  <div className="p-3 bg-slate-900/50 rounded-lg">
                    <p className="text-slate-400 text-sm">Failed Calls</p>
                    <p className="font-mono text-xl text-red-400">{cb.failedCalls.toLocaleString()}</p>
                  </div>
                  <div className="p-3 bg-slate-900/50 rounded-lg">
                    <p className="text-slate-400 text-sm">Successful Calls</p>
                    <p className="font-mono text-xl text-green-400">{cb.successfulCalls.toLocaleString()}</p>
                  </div>
                  <div className="p-3 bg-slate-900/50 rounded-lg sm:col-span-2">
                    <p className="text-slate-400 text-sm">Buffered Calls</p>
                    <p className="font-mono text-xl text-white">{cb.bufferedCalls.toLocaleString()}</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="bg-slate-800/50 border border-slate-600/50 rounded-xl p-12 text-center">
            <StatusBadge status="INFO" label="No circuit breakers found" />
            <p className="text-slate-400 mt-2">No circuit breakers registered in the system</p>
          </div>
        )}
      </main>
    </div>
  );
}