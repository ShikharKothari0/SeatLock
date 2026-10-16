// ThemeToggle - Toggles between dark/light mode with Sun/Moon icons

import { Sun, Moon } from 'lucide-react';
import { useThemeStore } from '../../store/themeStore';

export function ThemeToggle() {
  const { theme, toggle } = useThemeStore();

  return (
    <button
      onClick={toggle}
      className="p-2 rounded-lg bg-slate-100/50 dark:bg-slate-800/50 border border-slate-300/50 dark:border-slate-600/50 hover:border-slate-500/50 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
      aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
      title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
    >
      {theme === 'dark' ? (
        <Sun className="w-5 h-5 text-amber-700 dark:text-amber-400" aria-hidden="true" />
      ) : (
        <Moon className="w-5 h-5 text-indigo-700 dark:text-indigo-400" aria-hidden="true" />
      )}
    </button>
  );
}
