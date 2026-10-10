import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading, StatusPill } from "../components/ui";
import { post } from "../api";
import { useAuth } from "../auth";
import useApi from "../useApi";

// Items still out hold the discharge. For each, staff can either send it home
// on loan (a manager approves) or, for items that are lost, ask a manager for an
// override. Managers approve or reject with a reason, or act at once themselves.
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

  const atHome = patient ? patient.items.filter(i => i.homeLoan) : [];
  const outstanding = patient ? patient.items.filter(i => i.status !== "available" && !i.homeLoan) : [];
  const lendable = outstanding.filter(i => i.status === "issued");
  const override = patient?.override;
  const pending = override?.status === "requested";
  const approved = override?.status === "approved";
  const homeLoan = patient?.homeLoan;
  const homeLoanPending = homeLoan?.status === "requested";

  async function send(path, body) {
    setSaving(true);
    setSaveError(null);
    try {
      await post(path, body);
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
    send(`/discharge/${patient.mrn}/override`, { reason });
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
              {atHome.length > 0 && <p>{atHome.length} item{atHome.length === 1 ? "" : "s"} going home on loan.</p>}
            </div>
          ) : approved ? (
            <div className="hold done">
              <span className="label"><Icon name="shield" />READY FOR DISCHARGE</span>
              <h2>Override approved</h2>
              <p>Approved by {override.decidedBy}: {override.decisionReason}</p>
              {override.requestedBy !== override.decidedBy && <p>Requested by {override.requestedBy}: {override.reason}</p>}
            </div>
          ) : pending || homeLoanPending ? (
            <div className="hold">
              <span className="label"><Icon name="shield" />WAITING FOR A MANAGER</span>
              <h2>{homeLoanPending ? `Home loan requested by ${homeLoan.requestedBy}` : `Override requested by ${override.requestedBy}`}</h2>
              <p>{homeLoanPending ? homeLoanSummary(homeLoan) : `Reason: ${override.reason}`}</p>
            </div>
          ) : (
            <div className="hold">
              <span className="label"><Icon name="pause" />DISCHARGE ON HOLD</span>
              <h2>{outstanding.length} item{outstanding.length === 1 ? "" : "s"} not accounted for</h2>
              <p>Return the equipment, send it home on loan, or {manager ? "approve an override" : "ask a manager to approve an override"}.</p>
              {override?.status === "rejected" && <p style={{ marginTop: 8 }}>Override rejected by {override.decidedBy}: {override.decisionReason}</p>}
              {homeLoan?.status === "rejected" && <p style={{ marginTop: 8 }}>Home loan rejected by {homeLoan.decidedBy}: {homeLoan.decisionReason}</p>}
            </div>
          )}

          {outstanding.map(i => <ItemRow item={i} key={i.id} />)}

          {atHome.length > 0 && (
            <>
              <div className="section"><h2 className="strong">On home loan</h2></div>
              {atHome.map(i => <ItemRow item={i} key={i.id} />)}
              {homeLoan?.status === "approved" && <p className="sub small">Approved by {homeLoan.decidedBy}{homeLoan.note ? ` · ${homeLoan.note}` : ""}</p>}
            </>
          )}

          {saveError && <div style={{ marginTop: 12 }}><Loading error={saveError} /></div>}

          {/* A manager deciding on a request */}
          {manager && homeLoanPending && (
            <Decision title="Home loan" reason={reason} setReason={setReason} saving={saving}
                      onApprove={() => send(`/home-loans/${homeLoan.id}/approve`, { reason })}
                      onReject={() => send(`/home-loans/${homeLoan.id}/reject`, { reason })} />
          )}
          {manager && pending && !homeLoanPending && outstanding.length > 0 && (
            <Decision title="Override" reason={reason} setReason={setReason} saving={saving}
                      onApprove={() => send(`/discharge/overrides/${override.id}/approve`, { reason })}
                      onReject={() => send(`/discharge/overrides/${override.id}/reject`, { reason })} />
          )}
          {!manager && (pending || homeLoanPending) && (
            <p className="sub small">A manager will approve or reject this. Ask them to open Manage → Approvals.</p>
          )}

          {/* Nothing waiting: send items home, or ask for an override */}
          {outstanding.length > 0 && !approved && !pending && !homeLoanPending && (
            <>
              <Link className="btn" to="/scan"><Icon name="scan" />Scan return now</Link>

              {lendable.length > 0 && (
                <HomeLoanForm patient={patient} items={lendable} manager={manager} saving={saving}
                              onSubmit={body => send("/home-loans", body)} />
              )}

              <div className="override">
                <Icon name="shield" className="muted" /><strong>{manager ? "Approve discharge anyway" : "Item lost? Ask a manager"}</strong><span>Reason required</span>
              </div>
              <form onSubmit={submitOverride}>
                <input type="text" required placeholder={manager ? "Reason for approving the discharge *" : "Why should the patient leave with items out? *"}
                       value={reason} onChange={e => setReason(e.target.value)} />
                <button className="btn secondary" style={{ marginTop: 12 }} disabled={saving || !reason.trim()}>
                  {manager ? "Approve discharge" : "Request override"}
                </button>
              </form>
            </>
          )}
          <p className="sub small">Requests and decisions are recorded in the audit log.</p>
        </>
      )}
      <BottomNav />
    </main>
  );
}

function homeLoanSummary(homeLoan) {
  const items = homeLoan.items.map(i => `${i.id} until ${formatDate(i.dueOn)}`).join(", ");
  return homeLoan.note ? `${items} · ${homeLoan.note}` : items;
}

function formatDate(iso) {
  return new Date(`${iso}T12:00:00`).toLocaleDateString("en-IE", { day: "numeric", month: "short" });
}

function ItemRow({ item }) {
  return (
    <Link className="card loaned" to={`/item/${item.id}`}>
      <Icon name={item.category === "Wheelchairs" ? "wheelchair" : "layers"} />
      <div>
        <div className="name">{item.name}</div>
        <div className="sub tight">{item.id} · {item.note}</div>
        <StatusPill status={item.status} />
      </div>
    </Link>
  );
}

function Decision({ title, reason, setReason, saving, onApprove, onReject }) {
  return (
    <>
      <div className="override"><Icon name="shield" className="muted" /><strong>{title}: your decision</strong><span>Reason required</span></div>
      <input type="text" required placeholder="Reason for approving or rejecting *" value={reason} onChange={e => setReason(e.target.value)} />
      <div className="decision">
        <button className="btn" disabled={saving || !reason.trim()} onClick={onApprove}>Approve</button>
        <button className="btn danger" disabled={saving || !reason.trim()} onClick={onReject}>Reject</button>
      </div>
    </>
  );
}

// Choose which items go home and when each is due back (blank = the type's usual loan length)
function HomeLoanForm({ patient, items, manager, saving, onSubmit }) {
  const [chosen, setChosen] = useState({});
  const [note, setNote] = useState("");
  const today = new Date().toISOString().slice(0, 10);

  useEffect(() => {
    setChosen(Object.fromEntries(items.map(i => [i.id, { on: true, dueOn: "" }])));
  }, [items.map(i => i.id).join()]); // eslint-disable-line react-hooks/exhaustive-deps

  const update = (id, change) => setChosen({ ...chosen, [id]: { ...chosen[id], ...change } });
  const selected = items.filter(i => chosen[i.id]?.on);

  function submit(event) {
    event.preventDefault();
    onSubmit({
      mrn: patient.mrn,
      items: selected.map(i => ({ equipmentId: i.id, dueOn: chosen[i.id].dueOn || null })),
      note: note.trim() || null,
    });
  }

  return (
    <form className="card stack editor" style={{ marginTop: 16 }} onSubmit={submit}>
      <h2>Send home on loan</h2>
      <p className="sub tight">{manager ? "The items go home on loan as soon as you confirm." : "A manager approves home loans before the patient leaves."}</p>
      {items.map(i => (
        <div className="form-grid" key={i.id}>
          <label className="field inline">
            <span>{i.id} · {i.name}</span>
            <input type="checkbox" className="switch" checked={chosen[i.id]?.on ?? false} onChange={e => update(i.id, { on: e.target.checked })} />
          </label>
          <label className="field">
            <span className="hint">Return by</span>
            <input type="date" min={today} value={chosen[i.id]?.dueOn ?? ""} disabled={!chosen[i.id]?.on}
                   onChange={e => update(i.id, { dueOn: e.target.value })} aria-label={`Return date for ${i.id}`} />
          </label>
        </div>
      ))}
      <label className="field"><span>Note</span>
        <input type="text" maxLength={200} placeholder="Optional, e.g. community OT to collect" value={note} onChange={e => setNote(e.target.value)} />
        <span className="hint">Leave a return date blank for the usual loan length. Don't include names or contact details.</span>
      </label>
      <button className="btn" disabled={saving || !selected.length}>
        {manager ? "Send home on loan" : "Request home loan"}
      </button>
    </form>
  );
}
