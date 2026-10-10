import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading, StatusPill } from "../components/ui";
import { post } from "../api";
import { useAuth } from "../auth";
import useApi from "../useApi";

// Items still out hold the discharge. Staff ask a manager for an override with
// a reason; a manager approves or rejects it with a reason, or approves
// straight away when doing the check themselves.
export default function Discharge() {
  const { user } = useAuth();
  const manager = user.role === "MANAGER";
  const [params, setParams] = useSearchParams();
  const mrn = params.get("mrn") || "";
  const [search, setSearch] = useState(mrn);
  const { data: patient, error, reload } = useApi(mrn && `/discharge/${encodeURIComponent(mrn)}`);
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  const outstanding = patient ? patient.items.filter(i => i.status !== "available") : [];
  const override = patient?.override;
  const pending = override?.status === "requested";
  const approved = override?.status === "approved";

  async function send(path) {
    setSaving(true);
    setSaveError(null);
    try {
      await post(path, { reason });
      setReason("");
      reload();
    } catch (e) {
      setSaveError(e);
      if (e.status === 409) reload();
    } finally {
      setSaving(false);
    }
  }

  function submitOverride(event) {
    event.preventDefault();
    send(`/discharge/${patient.mrn}/override`);
  }

  return (
    <main className="app">
      <AppBar title="Discharge" />
      <h1>Check before discharge</h1>
      <p className="sub">Search by MRN to account for loaned items.</p>

      <form className="search" onSubmit={e => { e.preventDefault(); setParams(search.trim() ? { mrn: search.trim() } : {}); }}>
        <Icon name="search" />
        <input type="text" placeholder="MRN, e.g. 4821" value={search} onChange={e => setSearch(e.target.value)} aria-label="Search by MRN" />
      </form>

      {!mrn ? <p className="sub">Enter the patient's MRN and press Enter.</p> : !patient ? <Loading error={error} /> : (
        <>
          <div className="patient"><strong>{patient.ward}{patient.bed ? ` · Bed ${patient.bed}` : ""}</strong><span>MRN ••••{patient.mrn}</span></div>

          {!outstanding.length ? (
            <div className="hold done">
              <span className="label"><Icon name="check" />READY FOR DISCHARGE</span>
              <h2>All equipment accounted for</h2>
            </div>
          ) : approved ? (
            <div className="hold done">
              <span className="label"><Icon name="shield" />READY FOR DISCHARGE</span>
              <h2>Override approved</h2>
              <p>Approved by {override.decidedBy}: {override.decisionReason}</p>
              {override.requestedBy !== override.decidedBy && <p>Requested by {override.requestedBy}: {override.reason}</p>}
            </div>
          ) : pending ? (
            <div className="hold">
              <span className="label"><Icon name="shield" />WAITING FOR A MANAGER</span>
              <h2>Override requested by {override.requestedBy}</h2>
              <p>Reason: {override.reason}</p>
            </div>
          ) : (
            <div className="hold">
              <span className="label"><Icon name="pause" />DISCHARGE ON HOLD</span>
              <h2>{outstanding.length} item{outstanding.length === 1 ? "" : "s"} not accounted for</h2>
              <p>Return the equipment or {manager ? "approve an override" : "ask a manager to approve an override"} before completing the check.</p>
              {override?.status === "rejected" && <p style={{ marginTop: 8 }}>Last request rejected by {override.decidedBy}: {override.decisionReason}</p>}
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

          {outstanding.length > 0 && !approved && (
            <>
              {!pending && <Link className="btn" to="/scan"><Icon name="scan" />Scan return now</Link>}
              {saveError && <div style={{ marginTop: 12 }}><Loading error={saveError} /></div>}

              {pending && manager ? (
                <>
                  <div className="override"><Icon name="shield" className="muted" /><strong>Your decision</strong><span>Reason required</span></div>
                  <input type="text" required placeholder="Reason for approving or rejecting *" value={reason} onChange={e => setReason(e.target.value)} />
                  <div className="decision">
                    <button className="btn" disabled={saving || !reason.trim()} onClick={() => send(`/discharge/overrides/${override.id}/approve`)}>Approve</button>
                    <button className="btn danger" disabled={saving || !reason.trim()} onClick={() => send(`/discharge/overrides/${override.id}/reject`)}>Reject</button>
                  </div>
                </>
              ) : pending ? (
                <p className="sub small">A manager will approve or reject this. Ask them to open Manage → Discharge approvals.</p>
              ) : (
                <>
                  <div className="override">
                    <Icon name="shield" className="muted" /><strong>{manager ? "Approve discharge" : "Ask a manager to approve"}</strong><span>Reason required</span>
                  </div>
                  <form onSubmit={submitOverride}>
                    <input type="text" required placeholder={manager ? "Reason for approving the discharge *" : "Why should the patient leave with items out? *"}
                           value={reason} onChange={e => setReason(e.target.value)} />
                    <button className="btn secondary" style={{ marginTop: 12 }} disabled={saving || !reason.trim()}>
                      {manager ? "Approve discharge" : "Request approval"}
                    </button>
                  </form>
                </>
              )}
              <p className="sub small">Requests and decisions are recorded in the audit log.</p>
            </>
          )}
        </>
      )}
      <BottomNav />
    </main>
  );
}
