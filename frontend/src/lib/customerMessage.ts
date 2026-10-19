// Translate reservation failures at the customer UI boundary. Keep API diagnostics
// intact for developer tools, but never display arbitrary backend messages here.
export function customerMessage(error: unknown, fallback: string): string {
  const failure = error as { status?: number; message?: string } | null;
  const message = failure?.message ?? '';
  if (failure?.status === 401) return 'Please sign in again to continue.';
  if (failure?.status === 403) return 'You can’t access this reservation. Please sign in with the account used to book.';
  if (failure?.status === 429) return 'Please wait 10 seconds before trying again.';
  if (failure?.status === 409) {
    if (/sales.*not open/i.test(message)) return 'Ticket sales are currently closed for this event.';
    if (/quote|price/i.test(message)) return 'Return to checkout to review your updated total.';
    if (/expir/i.test(message)) return 'Your reservation expired. Please select your seats again.';
    return 'Some seats are no longer available. Please review your selection.';
  }
  return fallback;
}
