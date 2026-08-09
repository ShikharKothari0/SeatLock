// KafkaWidget - Shows Kafka event status and topic

import { StatusBadge } from '../common/StatusBadge';
import { formatRelativeTime } from '../../lib/formatters';
import type { DevEvent } from '../../store/developerModeStore';

interface KafkaWidgetProps {
  event: DevEvent | null;
}

export function KafkaWidget({ event }: KafkaWidgetProps) {
  const kafka = event?.kafka;

  if (!kafka) {
    return (
      <div className="bg-slate-900/50 border border-slate-700/50 rounded-lg p-4">
        <h4 className="font-mono text-xs text-slate-400 uppercase tracking-wider mb-3">Kafka</h4>
        <p className="text-slate-500 text-sm">No Kafka event</p>
      </div>
    );
  }

  return (
    <div className="bg-slate-900/50 border border-slate-700/50 rounded-lg p-4">
      <h4 className="font-mono text-xs text-slate-400 uppercase tracking-wider mb-3">Kafka</h4>
      <div className="space-y-2">
        <div>
          <span className="text-slate-500 text-xs">Status</span>
          <StatusBadge status={kafka.status} className="ml-2" />
        </div>
        <div>
          <span className="text-slate-500 text-xs">Topic</span>
          <p className="font-mono text-xs text-white break-all mt-0.5">{kafka.topic}</p>
        </div>
        {kafka.partition !== undefined && (
          <div>
            <span className="text-slate-500 text-xs">Partition</span>
            <p className="font-mono text-xs text-white mt-0.5">{kafka.partition}</p>
          </div>
        )}
        {kafka.offset !== undefined && (
          <div>
            <span className="text-slate-500 text-xs">Offset</span>
            <p className="font-mono text-xs text-white mt-0.5">{kafka.offset}</p>
          </div>
        )}
        <div>
          <span className="text-slate-500 text-xs">Event Time</span>
          <p className="font-mono text-xs text-white mt-0.5">{formatRelativeTime(event.timestamp)}</p>
        </div>
      </div>
    </div>
  );
}