import { useState } from "react";
import { Link } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading } from "../components/ui";
import { post } from "../api";
import useApi from "../useApi";

export default function AuditWalk() {
  const { data: audit, error, reload } = useApi("/audit");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [finished, setFinished] = useState(null);
  const percent = audit?.total ? Math.round((audit.found * 100) / audit.total) : 0;
  const noWalk = error?.status === 404;

  async function run(action) {
    setSaving(true);
    setSaveError(null);
    try {
      await action();
      reload();
    } catch (e) {
      setSaveError(e);
      if (e.status === 409) reload();
    } finally {
      setSaving(false);
    }
  }

  function finish() {
    if (!window.confirm("Finish the walk? Items not found will be marked missing.")) return;
    run(async () => setFinished(await post("/audit/finish")));
  }

  return (
    <main className="app">
      <AppBar title="Audit walk" sync={saving ? "saving" : "synced"} />
      {finished && (
        <div className="callout" style={{ marginBottom: 20 }}>
          <Icon name="check" />
          <span>Walk finished: {finished.found} of {finished.total} found{finished.markedMissing ? `, ${finished.markedMissing} marked missing` : ""}.</span>
        </div>
      )}
      {saveError && <Loading error={saveError} />}

      {noWalk ? <StartWalk saving={saving} onStart={body => run(() => post("/audit", body))} /> : !audit ? <Loading error={error} /> : (
        <>
          <div className="where">
            <div><h2>{audit.area}</h2><p className="sub tight">Started {audit.started} · {audit.by}</p></div>
            <Icon name="route" />
          </div>

          <div className="card progress">
            <div className="ring" style={{ "--p": percent }}><span>{percent}%<small>complete</small></span></div>
            <div>
              <p className="found">{audit.found} of {audit.total} found</p>
              <p className="sub">{audit.total - audit.found} items still to check</p>
              <p className="sub">Saved on this device</p>
            </div>
          </div>

          <div className="callout"><Icon name="cloudoff" />Keep scanning. Changes sync when connected.</div>

          <div className="section"><h2 className="strong">Still to find</h2></div>
          <div className="card rows">
            {audit.missing.map(e => (
              <Link className="row" to={`/item/${e.id}`} key={e.id}>
                <Icon name="search" className="muted" />
                <span className="body"><strong>{e.id} · {e.name}</strong><span className="meta">Expected: {e.location}</span></span>
              </Link>
            ))}
            {!audit.missing.length && <p className="sub" style={{ margin: "12px 0" }}>Everything expected has been found.</p>}
          </div>

          <div style={{ marginTop: 30 }}>
            <Link className="btn" to="/scan?audit=1"><Icon name="scan" />Scan next item</Link>
            <button className="btn secondary" disabled={saving} onClick={finish}><Icon name="check" />Finish walk</button>
            <p className="sub small">You can pause and resume this walk at any time. Finishing marks anything not found as missing.</p>
          </div>
        </>
      )}
      <BottomNav />
    </main>
  );
}

// No walk in progress: choose where to check
function StartWalk({ saving, onStart }) {
  const { data: locations, error } = useApi("/locations");
  const [chosen, setChosen] = useState([]);
  const toggle = name => setChosen(chosen.includes(name) ? chosen.filter(n => n !== name) : [...chosen, name]);

  return (
    <>
      <h1>No walk in progress</h1>
      <p className="sub">Choose the areas to check. Everything recorded there now becomes the list to find.</p>
      {!locations ? <Loading error={error} /> : (
        <>
          <div className="chips filters">
            {locations.map(l => (
              <button key={l.name} className={`chip ${chosen.includes(l.name) ? "on" : ""}`} onClick={() => toggle(l.name)}>
                {chosen.includes(l.name) && <Icon name="check" />}{l.name}
              </button>
            ))}
          </div>
          <button className="btn" disabled={saving || !chosen.length} onClick={() => onStart({ locations: chosen })}>
            <Icon name="route" />Start audit walk
          </button>
        </>
      )}
    </>
  );
}
