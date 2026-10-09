import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, ItemCard, Loading } from "../components/ui";
import { post } from "../api";
import useApi from "../useApi";

// One primary action for each status
const ACTIONS = {
  issued: { advice: "Return this item to the OT store, then check it is clean and undamaged.", icon: "in", text: "Return and check", to: id => `/return/${id}` },
  available: { advice: "Choose the destination ward and record the loan.", icon: "out", text: "Issue to ward", to: id => `/waitlist?allocate=${id}` },
  inspect: { advice: "Confirm cleaning and safety checks are complete.", icon: "check", text: "Mark inspected" },
  missing: { advice: "This item is reported missing. Send it to an audit or supervisor review.", icon: "route", text: "Open audit walk", to: () => "/audit" },
};

export default function ScanResult() {
  const { id } = useParams();
  const { data: item, error, reload } = useApi(`/equipment/${encodeURIComponent(id)}`);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  async function markInspected() {
    setSaving(true);
    setSaveError(null);
    try {
      await post(`/equipment/${item.id}/inspect`);
      reload();
    } catch (e) {
      setSaveError(e);
    } finally {
      setSaving(false);
    }
  }

  const action = item && ACTIONS[item.status];
  return (
    <main className="app flush">
      <AppBar title="Scan result" sync={saving ? "saving" : "synced"} />

      <div className="camera short">
        <span className="chair" /><span className="bench" />
        <span className="label"><Icon name="qr" />{id}</span>
      </div>

      <section className="sheet">
        <Link className="close" to="/scan" aria-label="Close"><Icon name="close" /></Link>
        {!item ? (
          <>
            <h1>{error?.status === 404 ? "Item not found" : "Looking up…"}</h1>
            <p className="sub">Equipment ID · {id}</p>
            {error && <div style={{ marginTop: 20 }}><Loading error={error} /></div>}
            <Link className="btn" style={{ marginTop: 28 }} to="/scan"><Icon name="scan" />Scan again</Link>
          </>
        ) : (
          <>
            <h1>Item found</h1>
            <p className="sub">Equipment ID matched · {item.id}</p>
            <div style={{ marginTop: 24 }}>
              <ItemCard item={item}><p className="note">{item.note}</p></ItemCard>
            </div>
            <p className="advice">{action.advice}</p>
            {saveError && <Loading error={saveError} />}
            {action.to ? (
              <Link className="btn" to={action.to(item.id)}><Icon name={action.icon} />{action.text}</Link>
            ) : (
              <button className="btn" onClick={markInspected} disabled={saving}><Icon name={action.icon} />{action.text}</button>
            )}
          </>
        )}
      </section>
    </main>
  );
}
