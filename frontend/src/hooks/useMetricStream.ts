// useMetricStream - Wraps native EventSource for SSE metrics stream

import { useEffect, useState, useCallback, useRef } from 'react';
import type { MetricStreamSnapshot } from '../types/metrics';
import { SSE_EVENTS } from '../lib/constants';

interface UseMetricStreamReturn {
  snapshot: MetricStreamSnapshot | null;
  connected: boolean;
  error: Error | null;
}

export function useMetricStream(): UseMetricStreamReturn {
  const [snapshot, setSnapshot] = useState<MetricStreamSnapshot | null>(null);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const eventSourceRef = useRef<EventSource | null>(null);

  const connect = useCallback(() => {
    // Close existing connection if any
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    const es = new EventSource('/api/admin/metrics/stream');
    eventSourceRef.current = es;

    es.onopen = () => {
      setConnected(true);
      setError(null);
    };

    es.addEventListener(SSE_EVENTS.METRICS, (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data) as MetricStreamSnapshot;
        setSnapshot(data);
      } catch (err) {
        console.error('Failed to parse metrics SSE event:', err);
      }
    });

    es.onerror = (err) => {
      setConnected(false);
      setError(new Error('SSE connection error'));
      // EventSource auto-reconnects natively
    };
  }, []);

  useEffect(() => {
    connect();

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
    };
  }, [connect]);

  return { snapshot, connected, error };
}