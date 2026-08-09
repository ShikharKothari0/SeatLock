// DeveloperConsole - Slide-in panel from right with Framer Motion

import { motion, AnimatePresence } from 'framer-motion';
import { useDeveloperModeStore } from '../../store/developerModeStore';
import { DeveloperToggle } from './DeveloperToggle';
import { RedisLockWidget } from './RedisLockWidget';
import { DatabaseWidget } from './DatabaseWidget';
import { CacheWidget } from './CacheWidget';
import { KafkaWidget } from './KafkaWidget';
import { ApiCallWidget } from './ApiCallWidget';
import { EventTimeline } from './EventTimeline';
import { X } from 'lucide-react';

const panelVariants = {
  hidden: { x: '100%', opacity: 0 },
  visible: { x: 0, opacity: 1 },
  exit: { x: '100%', opacity: 0 },
};

export function DeveloperConsole() {
  const { enabled, events, toggle } = useDeveloperModeStore();
  const latestEvent = events[0] || null;

  return (
    <AnimatePresence>
      {enabled && (
        <motion.div
          initial="hidden"
          animate="visible"
          exit="exit"
          variants={panelVariants}
          className="fixed right-0 top-16 bottom-0 z-[600] w-80 max-w-[280px] flex flex-col"
          style={{ backgroundColor: 'var(--color-surface-overlay)' }}
        >
          {/* Header */}
          <div className="flex items-center justify-between p-3 border-b border-slate-700/50 sticky top-0 bg-slate-800/95 backdrop-blur-sm z-10">
            <h3 className="font-mono text-xs font-semibold text-white uppercase tracking-wider">Developer Console</h3>
            <button
              onClick={toggle}
              className="p-1.5 text-slate-400 hover:text-white transition-colors"
              aria-label="Close developer console"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* ON/OFF Toggle */}
          <div className="p-3 border-b border-slate-700/50">
            <DeveloperToggle />
          </div>

          {/* Widgets - Latest Event Data */}
          <div className="flex-1 overflow-y-auto p-3 space-y-3">
            <RedisLockWidget event={latestEvent} />
            <DatabaseWidget event={latestEvent} />
            <CacheWidget event={latestEvent} />
            <KafkaWidget event={latestEvent} />
            <ApiCallWidget event={latestEvent} />
          </div>

          {/* Event Timeline */}
          <div className="p-3 border-t border-slate-700/50">
            <EventTimeline />
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}