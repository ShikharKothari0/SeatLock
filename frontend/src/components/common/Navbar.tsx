import { Link } from 'react-router-dom';
import { ThemeToggle } from './ThemeToggle';
import { DeveloperToggle } from '../developer/DeveloperToggle';
import { useSessionStore } from '../../store/sessionStore';
import { useAuthStore } from '../../store/authStore';
import api from '../../lib/api';
import { useState } from 'react';

export function Navbar() {
  const { pendingConfirmation, heldSeatIds } = useSessionStore();
  const user = useAuthStore(state => state.user);
  const [error, setError] = useState('');
  async function logout() {
    try {
      await api.post('/auth/logout');
      useSessionStore.getState().reset();
      window.location.assign('/login');
    } catch { setError('Unable to sign out. Please retry.'); }
  }
  return <header className="sticky top-0 z-[200] bg-white/80 dark:bg-slate-950/80 backdrop-blur-sm border-b border-slate-300/50 dark:border-slate-700/50">
    <nav aria-label="Main navigation" className="max-w-7xl mx-auto px-4 py-3 flex flex-wrap items-center justify-between gap-3">
      <Link to="/" className="shrink-0 text-3xl sm:text-4xl font-bold tracking-tight text-slate-900 dark:text-white">Seat<span className="text-indigo-700 dark:text-indigo-400">Lock</span></Link>
      <div className="flex flex-wrap items-center gap-4 text-sm text-slate-700 dark:text-slate-300">
        {user && (pendingConfirmation || heldSeatIds.length > 0) && <Link to={pendingConfirmation ? '/payment' : '/checkout'}>Resume reservation</Link>}
        {user ? <><Link to="/my-bookings">My Bookings</Link><span>{user.displayName}</span><button onClick={logout}>Sign out</button></> : <Link to="/login">Sign in</Link>}
        {user?.role === 'ADMIN' && <><Link to="/admin">Admin</Link><DeveloperToggle /></>}<ThemeToggle />
        {error && <span role="alert">{error}</span>}
      </div>
    </nav>
  </header>;
}
