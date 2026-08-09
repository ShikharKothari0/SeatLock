// DeveloperToggle - The DEV pill in the Navbar

import { useDeveloperModeStore } from '../../store/developerModeStore';

export function DeveloperToggle() {
  const { enabled, toggle } = useDeveloperModeStore();

  return (
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
  );
}