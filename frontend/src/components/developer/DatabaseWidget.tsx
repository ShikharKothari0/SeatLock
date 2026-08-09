// DatabaseWidget - Shows database transaction status and ID

import { StatusBadge } from '../common/StatusBadge';
import { formatRelativeTime } from '../../lib/formatters';
import type { DevEvent } from '../../store/developerModeStore';

interface DatabaseWidgetProps {
  event: DevEvent | null;
}

export function DatabaseWidget({ event }: DatabaseWidgetProps) {
  const database = event?.database;

  if (!database) {
    return (
      <div className="bg-slate-900/50 border border-slate-700/50 rounded-lg p-4">
        <h4 className="font-mono text-xs text-slate-400 uppercase tracking-wider mb-3">Database</h4>
        <p className="text-slate-500 text-sm">No DB event</p>
      </div>
    );
  }

  return (
    <div className="bg-slate-900/50 border border-slate-700/50 rounded-lg p-4">
      <h4 className="font-mono text-xs text-slate-400 uppercase tracking-wider mb-3">Database</h4>
      <div className="space-y-2">
        <div>
          <span className="text-slate-500 text-xs">Status</span>
          <StatusBadge status={database.status} className="ml-2" />
        </div>
        <div>
          <span className="text-slate-500 text-xs">Transaction ID</span>
          <p className="font-mono text-xs text-white break-all mt-0.5">
            {database.transactionId ?? '—'}
          </p>
        </div>
        <div>
          <span className="text-slate-500 text-xs">Event Time</span>
          <p className="font-mono text-xs text-white mt-0.5">{formatRelativeTime(event.timestamp)}</p>
        </div>
      </div>
    </div>
  );
}