// MetricCard types

export interface MetricCardProps {
  /** Label displayed below the value */
  label: string;
  /** Main value to display (number or formatted string) */
  value: string | number;
  /** Optional unit suffix (e.g., 'ms', 'MB', 'req/s') */
  unit?: string;
  /** Optional trend percentage (positive = good, negative = bad, undefined = no trend) */
  trend?: number;
  /** Additional CSS class names */
  className?: string;
}