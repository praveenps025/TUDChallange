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
        <span className="mark"><Icon name="scan" /></span>
        <span className="eyebrow">EQUIPTRACK OT</span>
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
        <p className="sub small" style={{ marginTop: 24 }}>
          Sample data mode: sign in as <strong>alex.morgan</strong> (manager) or <strong>sam.patel</strong> (OT staff) with any password.
        </p>
      )}
    </main>
  );
}
