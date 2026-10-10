import { useState } from "react";
import { Navigate, useLocation } from "react-router-dom";
import Icon from "../components/Icon";
import { Loading } from "../components/ui";
import { usingMock } from "../api";
import { useAuth } from "../auth";

export default function Login() {
  const { user, notice, login } = useAuth();
  const location = useLocation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  if (user) return <Navigate to={location.state?.from || "/"} replace />;

  async function demoSignIn(name) {
    setBusy(true);
    setError(null);
    try {
      await login(name, "demo");
    } catch (e) {
      setError(e);
      setBusy(false);
    }
  }

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username.trim(), password);
    } catch (e) {
      setError(e);
      setPassword("");
      setBusy(false);
    }
  }

  return (
    <main className="app login">
      <div className="brand">
        <span className="mark logo"><img src="/logo.png" alt="CareTrack" /></span>
        <span className="eyebrow">CARETRACK</span>
      </div>
      <h1>Sign in</h1>
      <p className="sub">Use your own account, even on a shared device.</p>

      {notice && !error && <div className="callout"><Icon name="alert" />{notice}</div>}

      <form className="stack" onSubmit={submit}>
        {error && <Loading error={error} />}
        <label className="field">
          <span>Username</span>
          <input type="text" autoComplete="username" autoCapitalize="none" spellCheck="false" autoFocus required
                 value={username} onChange={e => setUsername(e.target.value)} />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} />
        </label>
        <button className="btn" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button>
      </form>

      {usingMock && (
        <div className="card stack" style={{ marginTop: 28 }}>
          <strong>Demo with sample data</strong>
          <p className="sub tight">Nothing here is real. Pick an account to explore:</p>
          <button type="button" className="btn secondary" disabled={busy} onClick={() => demoSignIn("sam.patel")}>
            <Icon name="user" />Sign in as OT staff (Sam)
          </button>
          <button type="button" className="btn secondary" disabled={busy} onClick={() => demoSignIn("alex.morgan")}>
            <Icon name="grid" />Sign in as Manager (Alex)
          </button>
          <p className="sub small tight">Other demo logins: riley.chen (staff), jordan.lee (manager), any password.</p>
        </div>
      )}
    </main>
  );
}
