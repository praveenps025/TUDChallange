import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { AppBar, BottomNav, ItemCard, Loading } from "../components/ui";
import { post } from "../api";
import { useAuth } from "../auth";
import useApi from "../useApi";

// Lend an available item for a patient to use at home (from the item's page).
// Staff request it and a manager approves; a manager's own request goes through at once.
export default function NewHomeLoan() {
  const { user } = useAuth();
  const manager = user.role === "MANAGER";
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const id = params.get("item") || "";
  const { data: item, error } = useApi(id && `/equipment/${encodeURIComponent(id)}`);
  const [mrn, setMrn] = useState("");
  const [dueOn, setDueOn] = useState("");
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const today = new Date().toISOString().slice(0, 10);

  async function submit(event) {
    event.preventDefault();
    setSaving(true);
    setSaveError(null);
    try {
      const request = await post("/home-loans", { mrn, items: [{ equipmentId: item.id, dueOn: dueOn || null }], note: note.trim() || null });
      navigate(`/discharge?mrn=${request.mrn}`);
    } catch (e) {
      setSaveError(e);
      setSaving(false);
    }
  }

  return (
    <main className="app">
      <AppBar title="Home loan" />
      <h1>Lend for home use</h1>
      <p className="sub">{manager ? "The item is lent as soon as you confirm." : "A manager approves home loans before the item goes."}</p>

      {!item ? <Loading error={error} /> : (
        <form className="stack" style={{ marginTop: 20 }} onSubmit={submit}>
          <ItemCard item={item} />
          {saveError && <Loading error={saveError} />}
          {item.status !== "available" ? (
            <p className="sub">Only items in stock can be lent from here. Items already on a patient's ward loan are sent home from the discharge check.</p>
          ) : (
            <>
              <label className="field"><span>Patient MRN *</span>
                <input type="text" inputMode="numeric" required maxLength={30} autoComplete="off" value={mrn} onChange={e => setMrn(e.target.value)} />
                <span className="hint">Last 4 digits for a patient already on the system, otherwise the full MRN.</span>
              </label>
              <label className="field"><span>Return by</span>
                <input type="date" min={today} value={dueOn} onChange={e => setDueOn(e.target.value)} />
                <span className="hint">Leave blank for the usual loan length.</span>
              </label>
              <label className="field"><span>Note</span>
                <input type="text" maxLength={200} placeholder="Optional, e.g. community OT to collect" value={note} onChange={e => setNote(e.target.value)} />
                <span className="hint">Don't include names or contact details.</span>
              </label>
              <button className="btn" disabled={saving}>{manager ? "Lend for home use" : "Request home loan"}</button>
            </>
          )}
          <Link className="btn secondary" to={`/item/${id}`}>Back to item</Link>
        </form>
      )}
      <BottomNav />
    </main>
  );
}
