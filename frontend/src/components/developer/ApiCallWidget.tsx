// ApiCallWidget - Shows API call method, path, status code, and latency

import { StatusBadge } from '../common/StatusBadge';
import { formatRelativeTime, formatMs } from '../../lib/formatters';
import type { DevEvent } from '../../store/developerModeStore';

interface ApiCallWidgetProps {
  event: DevEvent | null;
}

export function ApiCallWidget({ event }: ApiCallWidgetProps) {
  const apiCall = event?.apiCall;

  if (!apiCall) {
    return (
      <div className="bg-slate-900/50 border border-slate-700/50 rounded-lg p-4">
        <h4 className="font-mono text-xs text-slate-400 uppercase tracking-wider mb-3">API Call</h4>
        <p className="text-slate-500 text-sm">No API call event</p>
      </div>
    );
  }

  return (
    <div className="bg-slate-900/50 border border-slate-700/50 rounded-lg p-4">
      <h4 className="font-mono text-xs text-slate-400 uppercase tracking-wider mb-3">API Call</h4>
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-slate-500 text-xs">Method</span>
          <span className="px-2 py-0.5 text-xs font-mono bg-violet-900/30 text-violet-400 rounded border border-violet-700/50">
            {apiCall.method}
          </span>
        </div>
        <div>
          <span className="text-slate-500 text-xs">Path</span>
          <p className="font-mono text-xs text-white break-all mt-0.5">{apiCall.path}</p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-slate-500 text-xs">Status</span>
          <StatusBadge status={apiCall.statusCode >= 400 ? 'FAILED' : 'SUCCESS'} label={String(apiCall.statusCode)} className="ml-2" />
        </div>
        <div className="flex items-center gap-2">
          <span className="text-slate-500 text-xs">Latency</span>
          <span className="font-mono text-xs text-white">{formatMs(apiCall.latencyMs)}</span>
        </div>
        <div>
          <span className="text-slate-500 text-xs">Event Time</span>
          <p className="font-mono text-xs text-white mt-0.5">{formatRelativeTime(event.timestamp)}</p>
        </div>
      </div>
    </div>
  );
}