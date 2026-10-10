import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { AppBar, BottomNav, Loading } from "../components/ui";
import { post } from "../api";
import useApi from "../useApi";

// Chosen by whoever makes the request, from the patient's condition.
// Urgent requests are served first.
export const PRIORITIES = [
  ["urgent", "Urgent", "Needed today: discharge is blocked, or a safety or pressure-injury risk"],
  ["soon", "Soon", "Needed within 2–3 days"],
  ["routine", "Routine", "Can wait its turn"],
];

// Staff and managers: ask for an item that is out of stock or in use
export default function NewRequest() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { data: types } = useApi("/equipment-types");
  const { data: wards } = useApi("/locations?kind=ward");
  const [form, setForm] = useState({ type: params.get("type") || "", ward: "", mrn: "", bed: "", priority: "", note: "" });
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const set = field => e => setForm({ ...form, [field]: e.target.value });

  useEffect(() => {
    if (!form.type && types?.length) setForm(f => ({ ...f, type: types[0].name }));
  }, [types, form.type]);

  async function submit(event) {
    event.preventDefault();
    setSaving(true);
    setSaveError(null);
    try {
      await post("/waitlist", { ...form, bed: form.bed || null, note: form.note || null });
      navigate("/waitlist");
    } catch (e) {
      setSaveError(e);
      setSaving(false);
    }
  }

  return (
    <main className="app">
      <AppBar title="New request" />
      <h1>Request equipment</h1>
      <p className="sub">The request joins the waitlist. You'll see it allocated when an item comes back.</p>

      {!types || !wards ? <Loading /> : (
        <form className="stack" style={{ marginTop: 24 }} onSubmit={submit}>
          {saveError && <Loading error={saveError} />}
          <label className="field"><span>Item needed *</span>
            <select className="input" required value={form.type} onChange={set("type")}>
              {types.map(t => <option key={t.name} value={t.name}>{t.name}</option>)}
            </select>
          </label>
          <div className="form-grid">
            <label className="field"><span>Ward *</span>
              <select className="input" required value={form.ward} onChange={set("ward")}>
                <option value="" disabled>Choose…</option>
                {wards.map(w => <option key={w.name} value={w.name}>{w.name}</option>)}
              </select>
            </label>
            <label className="field"><span>Bed</span>
              <input type="text" maxLength={10} value={form.bed} onChange={set("bed")} />
            </label>
          </div>
          <label className="field"><span>Patient MRN *</span>
            <input type="text" inputMode="numeric" required maxLength={30} autoComplete="off" value={form.mrn} onChange={set("mrn")} />
            <span className="hint">Last 4 digits for a patient already on the system; the full MRN for a new patient. Only the last 4 are ever shown.</span>
          </label>

          <div className="field">
            <span>Priority *</span>
            <div className="choices" role="radiogroup" aria-label="Priority">
              {PRIORITIES.map(([value, label, help]) => (
                <button type="button" key={value} role="radio" aria-checked={form.priority === value}
                        className={`choice ${form.priority === value ? "on" : ""}`} onClick={() => setForm({ ...form, priority: value })}>
                  <span className={`pill ${value}`}>{label}</span>
                  <span>{help}</span>
                </button>
              ))}
            </div>
          </div>

          <label className="field"><span>Note</span>
            <input type="text" maxLength={200} placeholder="Optional, e.g. needs a narrow chair" value={form.note} onChange={set("note")} />
            <span className="hint">Don't include names or clinical details.</span>
          </label>

          <button className="btn" disabled={saving || !form.priority}>{saving ? "Adding…" : "Add to waitlist"}</button>
        </form>
      )}
      <BottomNav />
    </main>
  );
}
