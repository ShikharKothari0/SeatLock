// Navbar - Sticky header with SeatLock logo, ThemeToggle, and DEV mode badge

import { useDeveloperModeStore } from '../../store/developerModeStore';
import { ThemeToggle } from './ThemeToggle';
import { DeveloperToggle } from '../developer/DeveloperToggle';

export function Navbar() {
  const { _enabled } = useDeveloperModeStore();

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
            <DeveloperToggle />
          </div>
        </div>
      </div>
    </header>
  );
}