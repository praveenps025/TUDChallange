import { Link } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading } from "../components/ui";
import useApi from "../useApi";

// Managers only: discharge overrides staff have asked for, oldest first.
// Each opens the patient's discharge check, where it is approved or rejected.
export default function Approvals() {
  const { data: pending, error } = useApi("/discharge/overrides");

  return (
    <main className="app">
      <AppBar title="Approvals" />
      {!pending ? <Loading error={error} /> : (
        <>
          <h1>{pending.length} waiting</h1>
          <p className="sub">Discharge overrides requested by staff.</p>
          <div className="list" style={{ marginTop: 20 }}>
            {pending.map(o => (
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
          {!pending.length && <p className="sub">Nothing to approve.</p>}
        </>
      )}
      <BottomNav />
    </main>
  );
}
