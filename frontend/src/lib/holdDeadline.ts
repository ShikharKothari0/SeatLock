// The server supplies a duration, so a skewed client clock cannot extend the hold.
export function localDeadline(hold: { expiresAt: string; serverTime: string }, requestStarted: number) {
  const duration = Date.parse(hold.expiresAt) - Date.parse(hold.serverTime);
  if (!Number.isFinite(duration)) throw new Error('Invalid server hold deadline. Refresh your reservation.');
  return requestStarted + Math.max(0, duration);
}
