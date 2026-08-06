// HoldTimer - Countdown timer with progress bar and expiry modal

import { useEffect, useState, useCallback } from 'react';
import { formatCountdown } from '../../lib/formatters';
import { StatusBadge } from '../common/StatusBadge';
import { LoadingSpinner } from '../common/LoadingSpinner';

interface HoldTimerProps {
  expiresAt: number; // Unix timestamp in ms
  onExpiry?: () => void;
}

export function HoldTimer({ expiresAt, onExpiry }: HoldTimerProps) {
  const [timeRemaining, setTimeRemaining] = useState<number>(0);
  const [showExpiryModal, setShowExpiryModal] = useState(false);
  const [progress, setProgress] = useState(100);

  // Calculate initial values
  useEffect(() => {
    const remaining = expiresAt - Date.now();
    setTimeRemaining(Math.max(0, remaining));
    const initialProgress = Math.max(0, (remaining / 300_000) * 100);
    setProgress(initialProgress);
  }, [expiresAt]);

  // Countdown interval
  useEffect(() => {
    if (timeRemaining <= 0) return;

    const interval = setInterval(() => {
      setTimeRemaining((prev) => {
        const next = prev - 1000;
        if (next <= 0) {
          setShowExpiryModal(true);
          onExpiry?.();
          return 0;
        }
        return next;
      });
      setProgress((prev) => Math.max(0, prev - (1000 / 300_000) * 100));
    }, 1000);

    return () => clearInterval(interval);
  }, [timeRemaining, onExpiry]);

  const handleBackToSelection = useCallback(() => {
    setShowExpiryModal(false);
    onExpiry?.();
  }, [onExpiry]);

  if (timeRemaining <= 0 && !showExpiryModal) {
    return null;
  }

  return (
    <>
      {/* Timer Display */}
      <div className="flex items-center justify-center gap-3 p-3 bg-slate-800/50 border border-slate-600/50 rounded-lg">
        <div className="flex items-center gap-2">
          <StatusBadge status="WAITING" label="Hold expires in" className="text-xs" />
          <span className="font-mono text-lg font-semibold text-white tabular-nums">
            {formatCountdown(timeRemaining)}
          </span>
        </div>
        {/* Progress bar */}
        <div className="flex-1 max-w-xs h-1.5 bg-slate-700 rounded-full overflow-hidden">
          <div
            className="h-full bg-indigo-500 transition-all duration-1000 ease-linear"
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>

      {/* Expiry Modal */}
      {showExpiryModal && (
        <div
          className="fixed inset-0 z-[500] flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm"
          onClick={() => handleBackToSelection()}
          role="dialog"
          aria-modal="true"
          aria-labelledby="expiry-title"
        >
          <div
            className="w-full max-w-md bg-slate-900 border border-slate-700 rounded-xl p-6 text-center"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-amber-500/20 flex items-center justify-center">
              <LoadingSpinner size="lg" className="text-amber-500" ariaLabel="Timer expired" />
            </div>
            <h2 id="expiry-title" className="text-xl font-bold text-white mb-2">
              Your hold has expired
            </h2>
            <p className="text-slate-400 mb-6">
              The 5-minute hold period has ended. Your selected seats have been released
              and are now available for other users.
            </p>
            <button
              onClick={handleBackToSelection}
              className="w-full px-4 py-3 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg transition-colors"
            >
              Back to seat selection
            </button>
          </div>
        </div>
      )}
    </>
  );
}