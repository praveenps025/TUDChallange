import { Link } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading } from "../components/ui";
import useApi from "../useApi";

// Managers only: what staff have asked for, oldest first. Each opens the
// patient's discharge check, where it is approved or rejected with a reason.
export default function Approvals() {
  const { data: overrides, error } = useApi("/discharge/overrides");
  const { data: homeLoans, error: homeError } = useApi("/home-loans/pending");

  return (
    <main className="app">
      <AppBar title="Approvals" />
      {!overrides || !homeLoans ? <Loading error={error || homeError} /> : (
        <>
          <h1>{overrides.length + homeLoans.length} waiting</h1>
          <p className="sub">Home loans and discharge overrides requested by staff.</p>

          <div className="section"><h2 className="strong">Home loans</h2></div>
          <div className="list">
            {homeLoans.map(r => (
              <Link className="row" to={`/discharge?mrn=${r.mrn}`} key={r.id}>
                <span className="tile"><Icon name="out" /></span>
                <span className="body">
                  <strong>{r.ward ? `${r.ward} · ` : ""}MRN ••••{r.mrn}</strong>
                  <span className="meta">{r.items.map(i => i.id).join(", ")} · asked by {r.requestedBy}, {r.requestedAt}</span>
                  {r.note && <span className="meta">“{r.note}”</span>}
                </span>
                <Icon name="chevron" className="chev" />
              </Link>
            ))}
          </div>
          {!homeLoans.length && <p className="sub">No home loans waiting.</p>}

          <div className="section"><h2 className="strong">Discharge overrides</h2></div>
          <div className="list">
            {overrides.map(o => (
              <Link className="row" to={`/discharge?mrn=${o.mrn}`} key={o.id}>
                <span className="tile"><Icon name="shield" /></span>
                <span className="body">
                  <strong>{o.ward}{o.bed ? ` · Bed ${o.bed}` : ""} · MRN ••••{o.mrn}</strong>
                  <span className="meta">{o.itemsOut} item{o.itemsOut === 1 ? "" : "s"} out · asked by {o.requestedBy}, {o.requestedAt}</span>
                  <span className="meta">“{o.reason}”</span>
                </span>
                <Icon name="chevron" className="chev" />
              </Link>
            ))}
          </div>
          {!overrides.length && <p className="sub">No overrides waiting.</p>}
        </>
      )}
      <BottomNav />
    </main>
  );
}
