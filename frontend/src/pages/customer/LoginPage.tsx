import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import api from '../../lib/api';
import { Eye, EyeOff } from 'lucide-react';

export function LoginPage() {
  const [register, setRegister] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
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
      <div>
        <label htmlFor="password" className="block">Password</label>
        <div className="relative">
          <input id="password" name="password" type={showPassword ? 'text' : 'password'} autoComplete={register ? 'new-password' : 'current-password'} required minLength={register ? 12 : 1} maxLength={72} className="block w-full border rounded p-2 pr-12 bg-transparent" />
          <button type="button" aria-label={showPassword ? 'Hide password' : 'Show password'} aria-controls="password" aria-pressed={showPassword} onClick={() => setShowPassword(!showPassword)} className="absolute inset-y-0 right-0 px-3 rounded focus-visible:outline-2 focus-visible:outline-indigo-500">
            {showPassword ? <EyeOff size={20} aria-hidden="true" /> : <Eye size={20} aria-hidden="true" />}
          </button>
        </div>
      </div>
      {register && <p className="text-sm">Use at least 12 characters.</p>}
      {error && <p role="alert" className="text-red-600 dark:text-red-400">{error}</p>}
      <button disabled={busy} className="bg-indigo-700 text-white rounded px-4 py-2 disabled:opacity-50">{busy ? 'Please wait…' : register ? 'Create account' : 'Sign in'}</button>
    </form>
    <button disabled={busy} className="mt-6 underline" onClick={() => { setRegister(!register); setShowPassword(false); setError(''); }}>{register ? 'Already registered? Sign in' : 'Create an account'}</button>
  </main>;
}
