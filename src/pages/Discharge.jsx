import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading, StatusPill } from "../components/ui";
import { post } from "../api";
import useApi from "../useApi";

export default function Discharge() {
  const [params, setParams] = useSearchParams();
  const mrn = params.get("mrn") || "";
  const [search, setSearch] = useState(mrn);
  const { data: patient, error, reload } = useApi(mrn && `/discharge/${encodeURIComponent(mrn)}`);
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  const outstanding = patient ? patient.items.filter(i => i.status !== "available") : [];

  async function requestOverride(event) {
    event.preventDefault();
    setSaving(true);
    setSaveError(null);
    try {
      await post(`/discharge/${patient.mrn}/override`, { reason });
      reload();
    } catch (e) {
      setSaveError(e);
      if (e.status === 409) reload();
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="app">
      <AppBar title="Discharge" sync={saving ? "saving" : "synced"} />
      <h1>Check before discharge</h1>
      <p className="sub">Search by MRN to account for loaned items.</p>

      <form className="search" onSubmit={e => { e.preventDefault(); setParams(search.trim() ? { mrn: search.trim() } : {}); }}>
        <Icon name="search" />
        <input type="text" placeholder="MRN, e.g. 4821" value={search} onChange={e => setSearch(e.target.value)} aria-label="Search by MRN" />
      </form>

      {!mrn ? <p className="sub">Enter the patient's MRN and press Enter.</p> : !patient ? <Loading error={error} /> : (
        <>
          <div className="patient"><strong>{patient.ward} · Bed {patient.bed}</strong><span>MRN ••••{patient.mrn}</span></div>

          {patient.override ? (
            <div className="hold done">
              <span className="label"><Icon name="shield" />OVERRIDE REQUESTED</span>
              <h2>Waiting for supervisor</h2>
              <p>Reason: {patient.override.reason}</p>
            </div>
          ) : outstanding.length ? (
            <div className="hold">
              <span className="label"><Icon name="pause" />DISCHARGE ON HOLD</span>
              <h2>{outstanding.length} item{outstanding.length === 1 ? "" : "s"} not accounted for</h2>
              <p>Return the equipment or request a supervisor override before completing the check.</p>
            </div>
          ) : (
            <div className="hold done">
              <span className="label"><Icon name="check" />READY FOR DISCHARGE</span>
              <h2>All equipment accounted for</h2>
            </div>
          )}

          {outstanding.map(i => (
            <Link className="card loaned" to={`/item/${i.id}`} key={i.id}>
              <Icon name={i.category === "Wheelchairs" ? "wheelchair" : "layers"} />
              <div>
                <div className="name">{i.name}</div>
                <div className="sub tight">{i.id} · {i.note}</div>
                <StatusPill status={i.status} />
              </div>
            </Link>
          ))}

          {outstanding.length > 0 && !patient.override && (
            <>
              <Link className="btn" to="/scan"><Icon name="scan" />Scan return now</Link>
              <div className="override">
                <Icon name="shield" className="muted" /><strong>Supervisor override</strong><span>Reason required</span>
              </div>
              <form onSubmit={requestOverride}>
                <input type="text" required placeholder="Enter reason to request an override *" value={reason} onChange={e => setReason(e.target.value)} />
              </form>
              {saveError && <div style={{ marginTop: 12 }}><Loading error={saveError} /></div>}
              <p className="sub small">Supervisor approval is recorded in the audit log.</p>
            </>
          )}
        </>
      )}
      <BottomNav />
    </main>
  );
}
