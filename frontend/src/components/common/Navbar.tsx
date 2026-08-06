// Navbar - Sticky header with SeatLock logo, ThemeToggle, and DEV mode badge

import { useDeveloperModeStore } from '../../store/developerModeStore';
import { ThemeToggle } from './ThemeToggle';

export function Navbar() {
  const { enabled, toggle } = useDeveloperModeStore();

  return (
    <header className="sticky top-0 z-[200] bg-slate-950/80 backdrop-blur-sm border-b border-slate-700/50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Left: SeatLock Logo */}
          <div className="flex items-center gap-2">
            <span className="text-xl font-bold text-white">
              Seat<span className="font-extrabold text-indigo-400">Lock</span>
            </span>
          </div>

          {/* Right: ThemeToggle + DEV badge */}
          <div className="flex items-center gap-3">
            <ThemeToggle />

            <button
              onClick={toggle}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-mono font-medium transition-all ${
                enabled
                  ? 'bg-green-900/30 text-green-400 border border-green-700/50'
                  : 'bg-slate-800 text-slate-500 border border-slate-600/50 hover:border-slate-500/50 hover:bg-slate-700'
              }`}
              aria-label={enabled ? 'Disable developer mode' : 'Enable developer mode'}
              title={enabled ? 'Developer mode: ON' : 'Developer mode: OFF'}
            >
              <span className={`w-1.5 h-1.5 rounded-full ${enabled ? 'bg-green-400' : 'bg-slate-500'}`} />
              DEV
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}