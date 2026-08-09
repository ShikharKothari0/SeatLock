// CacheWidget - Shows cache status and key

import { StatusBadge } from '../common/StatusBadge';
import { formatRelativeTime } from '../../lib/formatters';
import type { DevEvent } from '../../store/developerModeStore';

interface CacheWidgetProps {
  event: DevEvent | null;
}

export function CacheWidget({ event }: CacheWidgetProps) {
  const cache = event?.cache;

  if (!cache) {
    return (
      <div className="bg-slate-900/50 border border-slate-700/50 rounded-lg p-4">
        <h4 className="font-mono text-xs text-slate-400 uppercase tracking-wider mb-3">Cache</h4>
        <p className="text-slate-500 text-sm">No cache event</p>
      </div>
    );
  }

  return (
    <div className="bg-slate-900/50 border border-slate-700/50 rounded-lg p-4">
      <h4 className="font-mono text-xs text-slate-400 uppercase tracking-wider mb-3">Cache</h4>
      <div className="space-y-2">
        <div>
          <span className="text-slate-500 text-xs">Status</span>
          <StatusBadge status={cache.status} className="ml-2" />
        </div>
        <div>
          <span className="text-slate-500 text-xs">Key</span>
          <p className="font-mono text-xs text-white break-all mt-0.5">{cache.key}</p>
        </div>
        <div>
          <span className="text-slate-500 text-xs">Event Time</span>
          <p className="font-mono text-xs text-white mt-0.5">{formatRelativeTime(event.timestamp)}</p>
        </div>
      </div>
    </div>
  );
}