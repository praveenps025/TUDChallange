import { useState } from "react";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading } from "../components/ui";
import { post, put } from "../api";
import { ROLE_NAMES } from "../auth";
import useApi from "../useApi";

const BLANK = { username: "", name: "", team: "OT equipment team", role: "OT_STAFF", password: "" };

function status(user) {
  if (!user.active) return ["missing", "Deactivated"];
  if (user.locked) return ["inspect", "Locked"];
  return ["available", "Active"];
}

// Managers only (the route is guarded and the API checks the role too)
export default function Users() {
  const { data: users, error, reload } = useApi("/users");
  const [editing, setEditing] = useState(null); // a user, or BLANK for a new one
  const [password, setPassword] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [message, setMessage] = useState(null);

  const isNew = editing && !editing.id;
  const set = field => e => setEditing({ ...editing, [field]: e.target.type === "checkbox" ? e.target.checked : e.target.value });

  function open(user) {
    setEditing(user);
    setPassword("");
    setSaveError(null);
    setMessage(null);
  }

  async function run(action, done) {
    setSaving(true);
    setSaveError(null);
    try {
      await action();
      setMessage(done);
      reload();
    } catch (e) {
      setSaveError(e);
    } finally {
      setSaving(false);
    }
  }

  function save(event) {
    event.preventDefault();
    const { username, name, team, role, active } = editing;
    run(async () => {
      if (isNew) await post("/users", { username, name, team, role, password: editing.password });
      else await put(`/users/${editing.id}`, { name, team, role, active });
      setEditing(null);
    }, isNew ? `${name} can now sign in as ${username}.` : `${name} updated.`);
  }

  function resetPassword(event) {
    event.preventDefault();
    run(async () => {
      await post(`/users/${editing.id}/reset-password`, { password });
      setPassword("");
    }, `Password reset for ${editing.name}. Give them the new password in person.`);
  }

  return (
    <main className="app">
      <AppBar title="Users" />
      {!users ? <Loading error={error} /> : (
        <>
          <h1>{users.length} users</h1>
          <p className="sub">Tap someone to change their details, role or password.</p>

          {message && <div className="callout" style={{ marginTop: 20 }}><Icon name="check" />{message}</div>}

          {editing ? (
            <form className="card stack editor" style={{ marginTop: 20 }} onSubmit={save}>
              <h2>{isNew ? "Add user" : editing.name}</h2>
              {saveError && <Loading error={saveError} />}
              {isNew ? (
                <label className="field"><span>Username</span>
                  <input type="text" autoCapitalize="none" spellCheck="false" required value={editing.username} onChange={set("username")} />
                </label>
              ) : <p className="sub tight">Username: {editing.username}</p>}
              <label className="field"><span>Name</span><input type="text" required value={editing.name} onChange={set("name")} /></label>
              <label className="field"><span>Team</span><input type="text" required value={editing.team} onChange={set("team")} /></label>
              <label className="field"><span>Role</span>
                <select className="input" value={editing.role} onChange={set("role")}>
                  {Object.entries(ROLE_NAMES).map(([value, text]) => <option key={value} value={value}>{text}</option>)}
                </select>
              </label>
              {isNew ? (
                <label className="field"><span>Starting password</span>
                  <input type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={editing.password} onChange={set("password")} />
                </label>
              ) : (
                <label className="field inline"><span>Account active</span>
                  <input type="checkbox" className="switch" checked={editing.active} onChange={set("active")} />
                </label>
              )}
              <button className="btn" disabled={saving}>{isNew ? "Add user" : "Save changes"}</button>
              <button type="button" className="btn secondary" onClick={() => setEditing(null)}>Cancel</button>
            </form>
          ) : (
            <button className="btn" style={{ marginTop: 20 }} onClick={() => open(BLANK)}><Icon name="user" />Add user</button>
          )}

          {editing && !isNew && (
            <form className="card stack editor" onSubmit={resetPassword}>
              <h2>Reset password</h2>
              <p className="sub tight">Also unlocks the account after too many failed sign-ins.</p>
              <label className="field"><span>New password</span>
                <input type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} />
              </label>
              <button className="btn secondary" disabled={saving}><Icon name="key" />Reset password</button>
            </form>
          )}

          <div className="list" style={{ marginTop: 20 }}>
            {users.map(u => {
              const [kind, text] = status(u);
              return (
                <button className="row" key={u.id} onClick={() => open(u)}>
                  <span className="tile"><Icon name="user" /></span>
                  <span className="body"><strong>{u.name}</strong><span className="meta">{u.username} · {ROLE_NAMES[u.role]}</span></span>
                  <span className={`pill ${kind}`}>{text}</span>
                </button>
              );
            })}
          </div>
        </>
      )}
      <BottomNav />
    </main>
  );
}
