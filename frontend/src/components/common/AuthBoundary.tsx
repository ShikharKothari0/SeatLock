import { useEffect, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import api from '../../lib/api';
import { useAuthStore, type Account } from '../../store/authStore';
import { useSessionStore } from '../../store/sessionStore';
import { ReservationRecovery } from '../customer/ReservationRecovery';

export function AuthBoundary({ children }: { children: ReactNode }) {
  const { user, setUser } = useAuthStore();
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  const location = useLocation();
  const queries = useQueryClient();
  useEffect(() => {
    let active = true;
    const expire = () => { setUser(null); queries.clear(); setStatus('ready'); };
    const check = async () => {
      try {
        const { data } = await api.get<Account>('/auth/me');
        if (!active) return;
        if (useSessionStore.getState().userId !== data.id) {
          queries.clear();
          useSessionStore.getState().reset();
          useSessionStore.setState({ userId: data.id });
        }
        setUser(data); setStatus('ready');
      } catch (error) {
        if (!active) return;
        if ((error as { status?: number }).status === 401) expire();
        else setStatus('error');
      }
    };
    void check();
    window.addEventListener('seatlock-auth-expired', expire);
    window.addEventListener('focus', check);
    return () => { active = false; window.removeEventListener('seatlock-auth-expired', expire); window.removeEventListener('focus', check); };
  }, [attempt, queries, setUser]);

  if (status === 'loading') return <p role="status" className="p-8">Checking your session…</p>;
  if (status === 'error') return <main className="p-8"><p role="alert">Unable to check your session.</p><button onClick={() => setAttempt(attempt + 1)}>Retry</button></main>;
  if (location.pathname === '/login') return <>{children}</>;
  if (!user) return location.pathname === '/' ? <>{children}</> : <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />;
  if (location.pathname.startsWith('/admin') && user.role !== 'ADMIN') return <main className="p-8"><h1>Access denied</h1><p>This area requires an administrator account.</p></main>;
  return <ReservationRecovery key={user.id}>{children}</ReservationRecovery>;
}
