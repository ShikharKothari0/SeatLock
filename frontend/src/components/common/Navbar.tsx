import { Link } from 'react-router-dom';
import { ThemeToggle } from './ThemeToggle';
import { DeveloperToggle } from '../developer/DeveloperToggle';
import { useSessionStore } from '../../store/sessionStore';

export function Navbar() {
  const { pendingConfirmation, heldSeatIds } = useSessionStore();
  return <header className="sticky top-0 z-[200] bg-white/80 dark:bg-slate-950/80 backdrop-blur-sm border-b border-slate-300/50 dark:border-slate-700/50">
    <nav aria-label="Main navigation" className="max-w-7xl mx-auto px-4 py-3 flex flex-wrap items-center justify-between gap-3">
      <Link to="/" className="text-xl font-bold text-slate-900 dark:text-white">Seat<span className="text-indigo-700 dark:text-indigo-400">Lock</span></Link>
      <div className="flex flex-wrap items-center gap-4 text-sm text-slate-700 dark:text-slate-300">
        {(pendingConfirmation || heldSeatIds.length > 0) && <Link to={pendingConfirmation ? '/payment' : '/checkout'}>Resume reservation</Link>}
        <Link to="/my-bookings">My Bookings</Link><Link to="/admin">Admin</Link><ThemeToggle /><DeveloperToggle />
      </div>
    </nav>
  </header>;
}
