import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import api from '../../lib/api';

export function LoginPage() {
  const [register, setRegister] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [params] = useSearchParams();
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('');
    const form = new FormData(event.currentTarget);
    const email = String(form.get('email'));
    const password = String(form.get('password'));
    try {
      if (register) await api.post('/auth/register', { email, password, displayName: String(form.get('displayName')) });
      await api.post('/auth/login', new URLSearchParams({ email, password }), { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } });
      const next = params.get('next') ?? '/';
      window.location.replace(/^\/(?:events\/|checkout$|payment$|my-bookings$|booking-confirmed\/|admin(?:\/|$))/.test(next) ? next : '/');
    } catch (error) { setError((error as Error).message); setBusy(false); }
  }
  return <main className="max-w-md mx-auto p-8 text-slate-900 dark:text-slate-100">
    <h1 className="text-2xl font-bold mb-6">{register ? 'Create your account' : 'Sign in to SeatLock'}</h1>
    <form onSubmit={submit} className="space-y-4">
      {register && <label className="block">Name<input name="displayName" autoComplete="name" required maxLength={100} className="block w-full border rounded p-2 bg-transparent" /></label>}
      <label className="block">Email<input name="email" type="email" autoComplete="username" required maxLength={255} className="block w-full border rounded p-2 bg-transparent" /></label>
      <label className="block">Password<input name="password" type="password" autoComplete={register ? 'new-password' : 'current-password'} required minLength={register ? 12 : 1} maxLength={72} className="block w-full border rounded p-2 bg-transparent" /></label>
      {register && <p className="text-sm">Use at least 12 characters.</p>}
      {error && <p role="alert" className="text-red-600 dark:text-red-400">{error}</p>}
      <button disabled={busy} className="bg-indigo-700 text-white rounded px-4 py-2 disabled:opacity-50">{busy ? 'Please wait…' : register ? 'Create account' : 'Sign in'}</button>
    </form>
    <button disabled={busy} className="mt-6 underline" onClick={() => { setRegister(!register); setError(''); }}>{register ? 'Already registered? Sign in' : 'Create an account'}</button>
  </main>;
}
