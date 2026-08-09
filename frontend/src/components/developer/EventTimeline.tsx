// EventTimeline - Renders developer events as a scrollable list

import { useDeveloperModeStore } from '../../store/developerModeStore';
import type { DevEvent } from '../../store/developerModeStore';

interface EventTimelineProps {
  expanded?: boolean;
  onToggleExpand?: () => void;
}

function getEventSummary(event: DevEvent): string {
  const parts: string[] = [];

  if (event.redisLock) {
    parts.push(`${event.redisLock.status}`);
  }
  if (event.database) {
    parts.push(`${event.database.status}`);
  }
  if (event.cache) {
    parts.push(`${event.cache.status}`);
  }
  if (event.kafka) {
    parts.push(`${event.kafka.status}`);
  }
  if (event.apiCall) {
    parts.push(`${event.apiCall.method} ${event.apiCall.statusCode}`);
  }
  if (event.error) {
    parts.push(`ERROR: ${event.error.message}`);
  }

  return parts.join(' | ') || 'No details';
}

function getEventTypeColor(type: DevEvent['type']): string {
  switch (type) {
    case 'SEAT_HOLD': return 'text-violet-400';
    case 'BOOKING_CONFIRM': return 'text-green-400';
    case 'SEAT_RELEASE': return 'text-amber-400';
    case 'HOLD_EXPIRED': return 'text-red-400';
    case 'ERROR': return 'text-red-400';
    default: return 'text-slate-400';
  }
}

export function EventTimeline({ expanded = false, onToggleExpand }: EventTimelineProps) {
  const { events, clearEvents } = useDeveloperModeStore();
  const displayEvents = expanded ? events : events.slice(0, 10);

  if (events.length === 0) {
    return (
      <div className="bg-slate-900/50 border border-slate-700/50 rounded-lg p-4">
        <div className="flex items-center justify-between mb-3">
          <h4 className="font-mono text-xs text-slate-400 uppercase tracking-wider">Event Timeline</h4>
        </div>
        <p className="text-slate-500 text-sm text-center py-4">No events recorded</p>
      </div>
    );
  }

  return (
    <div className="bg-slate-900/50 border border-slate-700/50 rounded-lg">
      <div className="flex items-center justify-between p-3 border-b border-slate-700/50">
        <h4 className="font-mono text-xs text-slate-400 uppercase tracking-wider">Event Timeline</h4>
        <div className="flex items-center gap-2">
          <span className="font-mono text-xs text-slate-500">{events.length} events</span>
          {events.length > 10 && (
            <button
              onClick={onToggleExpand}
              className="px-2 py-0.5 text-xs font-mono text-slate-400 hover:text-white transition-colors"
            >
              {expanded ? 'Show Less' : 'Show All'}
            </button>
          )}
          <button
            onClick={clearEvents}
            className="px-2 py-0.5 text-xs font-mono text-red-400 hover:text-red-300 transition-colors"
            title="Clear all events"
          >
            Clear
          </button>
        </div>
      </div>
      <div className="max-h-64 overflow-y-auto p-2 space-y-1">
        {displayEvents.map((event, index) => (
          <div
            key={`${event.timestamp}-${index}`}
            className="p-2 rounded bg-slate-800/50 hover:bg-slate-800 transition-colors cursor-default"
          >
            <div className="flex items-center gap-2 mb-1">
              <span className="font-mono text-[10px] text-slate-500 whitespace-nowrap">
                {new Date(event.timestamp).toLocaleTimeString('en-US', { hour12: false })}.{
                  String(new Date(event.timestamp).getMilliseconds()).padStart(3, '0')
                }
              </span>
              <span className={`font-mono text-[10px] uppercase ${getEventTypeColor(event.type)}`}>
                {event.type}
              </span>
              {event.seatNumber && (
                <span className="font-mono text-[10px] text-white">{event.seatNumber}</span>
              )}
              {event.seatId && !event.seatNumber && (
                <span className="font-mono text-[10px] text-slate-400">{event.seatId.slice(-8)}</span>
              )}
            </div>
            <div className="font-mono text-[10px] text-slate-500">
              {getEventSummary(event)}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}