import { Link } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading } from "../components/ui";
import useApi from "../useApi";

export default function AuditWalk() {
  const { data: audit, error } = useApi("/audit");
  const percent = audit ? Math.round((audit.found * 100) / audit.total) : 0;

  return (
    <main className="app">
      <AppBar title="Audit walk" sync="offline" />
      {!audit ? <Loading error={error} /> : (
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
          </div>

          <div style={{ marginTop: 30 }}>
            <Link className="btn" to="/scan"><Icon name="scan" />Scan next item</Link>
            <p className="sub small">You can pause and resume this walk at any time.</p>
          </div>
        </>
      )}
      <BottomNav />
    </main>
  );
}
