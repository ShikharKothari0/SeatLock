// MetricCard - Large mono value with label and optional trend indicator

import { StatusBadge } from './StatusBadge';
import type { MetricCardProps } from './MetricCard.types';

export function MetricCard({
  label,
  value,
  unit,
  trend,
  className = '',
}: MetricCardProps) {
  const trendColor = trend === undefined
    ? undefined
    : trend > 0 ? 'text-green-400' : trend < 0 ? 'text-red-400' : 'text-slate-400';

  const trendIcon = trend === undefined
    ? undefined
    : trend > 0 ? '▲' : trend < 0 ? '▼' : '■';

  const trendLabel = trend === undefined
    ? undefined
    : trend > 0 ? 'positive' : trend < 0 ? 'negative' : 'neutral';

  return (
    <div className={`bg-slate-800/50 border border-slate-600/50 rounded-xl p-5 transition-colors hover:border-slate-500/50 ${className}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <p className="text-slate-400 text-sm font-medium tracking-wide uppercase mb-1.5">
            {label}
          </p>
          <div className="flex items-baseline gap-1.5">
            <span className="font-mono text-3xl font-semibold text-white tabular-nums">
              {value}
            </span>
            {unit && (
              <span className="font-mono text-lg text-slate-400 mt-1">
                {unit}
              </span>
            )}
          </div>
        </div>
        {trend !== undefined && (
          < StatusBadge
            status={trendLabel!.toUpperCase()}
            label={`${trendIcon} {Math.abs(trend).toFixed(1)}%`}
            className={`text-xs ${trendColor}`}
          />
        )}
      </div>
    </div>
  );
}