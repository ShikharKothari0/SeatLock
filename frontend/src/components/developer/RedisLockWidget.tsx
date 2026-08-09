// RedisLockWidget - Shows Redis lock status, key, TTL, and node

import { StatusBadge } from '../common/StatusBadge';
import { formatRelativeTime } from '../../lib/formatters';
import type { DevEvent } from '../../store/developerModeStore';

interface RedisLockWidgetProps {
  event: DevEvent | null;
}

export function RedisLockWidget({ event }: RedisLockWidgetProps) {
  const redisLock = event?.redisLock;

  if (!redisLock) {
    return (
      <div className="bg-slate-900/50 border border-slate-700/50 rounded-lg p-4">
        <h4 className="font-mono text-xs text-slate-400 uppercase tracking-wider mb-3">Redis Lock</h4>
        <p className="text-slate-500 text-sm">No lock event</p>
      </div>
    );
  }

  return (
    <div className="bg-slate-900/50 border border-slate-700/50 rounded-lg p-4">
      <h4 className="font-mono text-xs text-slate-400 uppercase tracking-wider mb-3">Redis Lock</h4>
      <div className="space-y-2">
        <div>
          <span className="text-slate-500 text-xs">Status</span>
          <StatusBadge status={redisLock.status} className="ml-2" />
        </div>
        <div>
          <span className="text-slate-500 text-xs">Key</span>
          <p className="font-mono text-xs text-white break-all mt-0.5">{redisLock.key}</p>
        </div>
        <div>
          <span className="text-slate-500 text-xs">TTL</span>
          <p className="font-mono text-xs text-white mt-0.5">
            {redisLock.ttl > 0 ? `${Math.ceil(redisLock.ttl / 1000)}s` : 'Expired'}
          </p>
        </div>
        <div>
          <span className="text-slate-500 text-xs">Node</span>
          <p className="font-mono text-xs text-white mt-0.5">{redisLock.node}</p>
        </div>
        <div>
          <span className="text-slate-500 text-xs">Event Time</span>
          <p className="font-mono text-xs text-white mt-0.5">{formatRelativeTime(event.timestamp)}</p>
        </div>
      </div>
    </div>
  );
}