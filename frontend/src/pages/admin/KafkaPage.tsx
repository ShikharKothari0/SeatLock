// KafkaPage - Kafka metrics page (placeholder)

import { AdminLayout } from '../../components/admin/AdminLayout';
import { Info } from 'lucide-react';

export function KafkaPage() {
  return (
    <AdminLayout>
      <div className="space-y-8">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 dark:text-white mb-2">Kafka Metrics</h1>
          <p className="text-slate-600 dark:text-slate-400">Kafka topic and consumer metrics</p>
        </div>

        <div className="bg-slate-100/50 dark:bg-slate-800/50 border border-slate-300/50 dark:border-slate-600/50 rounded-xl p-8 text-center">
          <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-amber-500/20 flex items-center justify-center">
            <Info className="w-8 h-8 text-amber-700 dark:text-amber-400" aria-hidden="true" />
          </div>
          <h3 className="text-xl font-semibold text-slate-900 dark:text-white mb-2">Kafka Metrics Not Available</h3>
          <p className="text-slate-600 dark:text-slate-400 mb-6 max-w-md mx-auto">
            The backend does not currently expose granular Kafka metrics through the admin API.
            Consumer lag and broker health require dedicated instrumentation.
          </p>
          <div className="space-y-2 text-left max-w-md mx-auto text-sm">
            <div className="p-3 bg-slate-50/50 dark:bg-slate-900/50 rounded-lg border border-slate-300/50 dark:border-slate-700/50">
              <p className="font-mono text-slate-900 dark:text-white">Planned metrics:</p>
              <ul className="list-disc list-inside text-slate-600 dark:text-slate-400 mt-1 space-y-1">
                <li>Consumer lag by topic/partition</li>
                <li>Messages produced/consumed rates</li>
                <li>Topic partition distribution</li>
                <li>Broker health status</li>
              </ul>
            </div>
            <div className="p-3 bg-slate-50/50 dark:bg-slate-900/50 rounded-lg border border-slate-300/50 dark:border-slate-700/50">
              <p className="font-mono text-slate-900 dark:text-white">Backend Topics:</p>
              <ul className="list-disc list-inside text-slate-600 dark:text-slate-400 mt-1 space-y-1">
                <li>seat-held</li>
                <li>seat-confirmed</li>
                <li>seat-released</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </AdminLayout>
  );
}
