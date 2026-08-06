// Formatters - Utility functions for formatting numbers, bytes, durations, etc.

/**
 * Formats milliseconds into human-readable string.
 * @param ms - Milliseconds
 * @returns Formatted string (e.g., "152ms", "1.5s", "2.3m")
 */
export function formatMs(ms: number): string {
  if (ms < 1000) {
    return `${Math.round(ms)}ms`;
  }
  if (ms < 60000) {
    return `${(ms / 1000).toFixed(1)}s`;
  }
  if (ms < 3600000) {
    return `${(ms / 60000).toFixed(1)}m`;
  }
  return `${(ms / 3600000).toFixed(1)}h`;
}

/**
 * Formats bytes into human-readable string with binary prefixes.
 * @param bytes - Number of bytes
 * @returns Formatted string (e.g., "128 MB", "1.5 GB")
 */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const units = ['KB', 'MB', 'GB', 'TB', 'PB'];
  let value = bytes;
  let unitIndex = -1;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex++;
  }
  return `${value.toFixed(value >= 100 ? 0 : value >= 10 ? 1 : 2)} ${units[unitIndex]}`;
}

/**
 * Formats a number with K/M/B suffixes.
 * @param n - Number to format
 * @returns Formatted string (e.g., "12.8K", "1.5M", "2.3B")
 */
export function formatCount(n: number): string {
  if (n < 1000) {
    return String(n);
  }
  if (n < 1000000) {
    return `${(n / 1000).toFixed(1)}K`;
  }
  if (n < 1000000000) {
    return `${(n / 1000000).toFixed(1)}M`;
  }
  return `${(n / 1000000000).toFixed(1)}B`;
}

/**
 * Formats a percentage value.
 * @param n - Percentage value (e.g., 92.14 for 92.14%)
 * @returns Formatted string (e.g., "92.1%")
 */
export function formatPercent(n: number): string {
  return `${n.toFixed(1)}%`;
}

/**
 * Formats a countdown from milliseconds to MM:SS.
 * @param ms - Milliseconds remaining
 * @returns Formatted string (e.g., "04:32")
 */
export function formatCountdown(ms: number): string {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}

/**
 * Formats an ISO timestamp to a relative time string.
 * @param isoString - ISO 8601 timestamp
 * @returns Relative time (e.g., "2s ago", "5m ago", "1h ago")
 */
export function formatRelativeTime(isoString: string): string {
  const date = new Date(isoString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();

  if (diffMs < 1000) return 'just now';
  if (diffMs < 60000) return `${Math.floor(diffMs / 1000)}s ago`;
  if (diffMs < 3600000) return `${Math.floor(diffMs / 60000)}m ago`;
  if (diffMs < 86400000) return `${Math.floor(diffMs / 3600000)}h ago`;
  return `${Math.floor(diffMs / 86400000)}d ago`;
}