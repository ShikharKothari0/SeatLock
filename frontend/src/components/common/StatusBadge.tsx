// StatusBadge - Maps status strings to colored badges with pulse animation for active states

import { getStatusBadgeClass, getStatusLabel } from '../../lib/constants';

interface StatusBadgeProps {
  /** Status string from the system (e.g., 'ACQUIRED', 'HELD', 'CONNECTED') */
  status: string;
  /** Optional custom label override */
  label?: string;
  /** Additional class names */
  className?: string;
}

const ACTIVE_STATUSES = new Set(['ACQUIRED', 'CONNECTED']);

export function StatusBadge({ status, label, className = '' }: StatusBadgeProps) {
  const badgeClass = getStatusBadgeClass(status);
  const displayLabel = label ?? getStatusLabel(status);
  const isActive = ACTIVE_STATUSES.has(status.toUpperCase());

  return (
    <span
      className={`status-badge ${badgeClass} ${isActive ? 'animate-pulse-soft' : ''} ${className}`}
      title={status}
    >
      {displayLabel}
    </span>
  );
}