import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { login } from '../api/endpoints';
import { setSession } from '../auth/session';
import { useSession } from '../auth/useSession';

export function LoginPage() {
  const session = useSession();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (session) return <Navigate to="/profiles" replace />;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setSession(await login(email.trim(), password));
      navigate('/profiles', { replace: true });
    } catch (e) {
      setError(e instanceof ApiError && e.status === 401 ? 'Incorrect email or password.' : 'Could not sign in. Is the API running?');
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-page">
      <div className="navbar__logo auth-page__logo">MARQUEE</div>
      <form className="auth-card" onSubmit={submit}>
        <h1>Sign in</h1>
        <label>
          Email
          <input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          Password
          <input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button type="submit" className="button button--primary button--block" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </main>
  );
}
