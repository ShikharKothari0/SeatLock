import { useEffect, useState } from 'react';
import { formatCountdown } from '../../lib/formatters';
import { HOLD_DURATION_MS } from '../../lib/constants';

export function HoldTimer({ expiresAt, onExpiry }: { expiresAt: number; onExpiry?: () => void }) {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const tick = () => setNow(Date.now());
    tick();
    const interval = window.setInterval(tick, 500);
    document.addEventListener('visibilitychange', tick);
    return () => { window.clearInterval(interval); document.removeEventListener('visibilitychange', tick); };
  }, [expiresAt]);
  const remaining = Math.max(0, expiresAt - now);
  const progress = Math.min(100, remaining / HOLD_DURATION_MS * 100);
  return <>
    <div className="p-3 bg-slate-100/50 dark:bg-slate-800/50 border border-slate-300/50 dark:border-slate-600/50 rounded-lg">
      <p className="text-sm text-slate-700 dark:text-slate-300">Estimated hold time remaining <span className="font-mono text-slate-900 dark:text-white">{formatCountdown(remaining)}</span></p>
      <div className="h-1.5 mt-2 bg-slate-700 rounded-full overflow-hidden">
        <div className="h-full bg-indigo-500" style={{ width: `${progress}%` }} />
      </div>
    </div>
    {remaining === 0 && <div className="fixed inset-0 z-[700] flex items-center justify-center p-4 bg-black/70" role="dialog" aria-modal="true" aria-labelledby="expiry-title">
      <div className="w-full max-w-md bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded-xl p-6 text-center">
        <h2 id="expiry-title" className="text-xl font-bold text-slate-900 dark:text-white">Your hold time has ended</h2>
        <p className="text-slate-600 dark:text-slate-400 my-4">Please return to seat selection and check availability again.</p>
        <button autoFocus onClick={onExpiry} className="w-full px-4 py-3 text-white bg-indigo-600 rounded-lg">Back to seat selection</button>
      </div>
    </div>}
  </>;
}
