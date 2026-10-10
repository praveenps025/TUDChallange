import { useState } from "react";
import { Link } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading } from "../components/ui";
import { post } from "../api";
import { initials, ROLE_NAMES, useAuth } from "../auth";

export default function Account() {
  const { user, logout } = useAuth();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [changed, setChanged] = useState(false);

  async function changePassword(event) {
    event.preventDefault();
    setSaving(true);
    setSaveError(null);
    setChanged(false);
    try {
      await post("/auth/change-password", { currentPassword, newPassword });
      setCurrentPassword("");
      setNewPassword("");
      setChanged(true);
    } catch (e) {
      setSaveError(e);
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="app">
      <AppBar title="Account" />

      <div className="card profile">
        <span className="avatar">{initials(user)}</span>
        <div>
          <div className="name">{user.name}</div>
          <div className="sub tight">{ROLE_NAMES[user.role]} · {user.team}</div>
        </div>
      </div>

      <button className="btn" style={{ marginTop: 16 }} onClick={logout}><Icon name="logout" />Sign out</button>
      <p className="sub small">On a shared device, sign out so the next person can sign in.</p>

      {user.role === "MANAGER" && (
        <Link className="row" style={{ marginTop: 20 }} to="/manage">
          <span className="tile"><Icon name="grid" /></span>
          <span className="body"><strong>Manage</strong><span className="meta">Equipment, approvals, insights and users</span></span>
          <Icon name="chevron" className="chev" />
        </Link>
      )}

      <div className="section"><h2 className="strong">Change password</h2></div>
      <form className="stack" onSubmit={changePassword}>
        {saveError && <Loading error={saveError} />}
        {changed && <div className="callout"><Icon name="check" />Password changed.</div>}
        <label className="field">
          <span>Current password</span>
          <input type="password" autoComplete="current-password" required value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} />
        </label>
        <label className="field">
          <span>New password</span>
          <input type="password" autoComplete="new-password" required minLength={8} maxLength={72}
                 value={newPassword} onChange={e => setNewPassword(e.target.value)} />
        </label>
        <button className="btn secondary" disabled={saving}><Icon name="key" />Change password</button>
      </form>

      <BottomNav />
    </main>
  );
}
