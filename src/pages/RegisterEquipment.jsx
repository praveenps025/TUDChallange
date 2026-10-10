import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading } from "../components/ui";
import EquipmentDetailsFields, { BLANK_DETAILS, detailsForApi } from "../components/EquipmentForm";
import { post } from "../api";
import useApi from "../useApi";

const NEW_TYPE = { name: "", category: "", labelPrefix: "", defaultLoanDays: 14 };

// Managers only. The server gives the item its ID (e.g. WC-032); the QR label opens next.
export default function RegisterEquipment() {
  const navigate = useNavigate();
  const { data: types, reload: reloadTypes } = useApi("/equipment-types");
  const { data: categories } = useApi("/categories");
  const { data: pools } = useApi("/pools");
  const { data: locations } = useApi("/locations");
  const [type, setType] = useState("");
  const [pool, setPool] = useState("MAIN");
  const [location, setLocation] = useState("OT store");
  const [details, setDetails] = useState(BLANK_DETAILS);
  const [newType, setNewType] = useState(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  useEffect(() => {
    if (!type && types?.length) setType(types[0].name);
  }, [types, type]);

  const chosen = types?.find(t => t.name === type);

  async function addType(event) {
    event.preventDefault();
    setSaving(true);
    setSaveError(null);
    try {
      const created = await post("/equipment-types", { ...newType, defaultLoanDays: Number(newType.defaultLoanDays) });
      reloadTypes();
      setType(created.name);
      setNewType(null);
    } catch (e) {
      setSaveError(e);
    } finally {
      setSaving(false);
    }
  }

  async function register(event) {
    event.preventDefault();
    setSaving(true);
    setSaveError(null);
    try {
      const item = await post("/equipment", { type, pool, location, details: detailsForApi(details) });
      navigate(`/manage/label/${item.id}?new=1`);
    } catch (e) {
      setSaveError(e);
      setSaving(false);
    }
  }

  if (!types || !pools || !locations) return <main className="app"><AppBar title="Register" /><Loading /></main>;

  return (
    <main className="app">
      <AppBar title="Register" />
      <h1>Register equipment</h1>
      <p className="sub">The item gets its ID automatically and is ready to issue. Only the name is required.</p>
      {saveError && <div style={{ marginTop: 20 }}><Loading error={saveError} /></div>}

      {newType ? (
        <form className="card stack editor" style={{ marginTop: 20 }} onSubmit={addType}>
          <h2>New equipment type</h2>
          <label className="field"><span>Type name</span>
            <input type="text" required maxLength={60} placeholder="e.g. Hoist" value={newType.name} onChange={e => setNewType({ ...newType, name: e.target.value })} />
          </label>
          <label className="field"><span>Category</span>
            <input type="text" required list="categories" maxLength={60} placeholder="Pick one or type a new category" value={newType.category}
                   onChange={e => setNewType({ ...newType, category: e.target.value })} />
            <datalist id="categories">{categories?.map(c => <option key={c.name} value={c.name} />)}</datalist>
          </label>
          <div className="form-grid">
            <label className="field"><span>ID prefix</span>
              <input type="text" required maxLength={10} placeholder="e.g. HO" value={newType.labelPrefix}
                     onChange={e => setNewType({ ...newType, labelPrefix: e.target.value.toUpperCase() })} />
            </label>
            <label className="field"><span>Loan length (days)</span>
              <input type="text" inputMode="numeric" required value={newType.defaultLoanDays}
                     onChange={e => setNewType({ ...newType, defaultLoanDays: e.target.value.replace(/\D/g, "") })} />
            </label>
          </div>
          <button className="btn" disabled={saving}>Add type</button>
          <button type="button" className="btn secondary" onClick={() => setNewType(null)}>Cancel</button>
        </form>
      ) : (
        <form className="stack" style={{ marginTop: 24 }} onSubmit={register}>
          <label className="field"><span>Equipment type *</span>
            <select className="input" value={type} onChange={e => setType(e.target.value)}>
              {types.map(t => <option key={t.name} value={t.name}>{t.name} ({t.labelPrefix})</option>)}
            </select>
            {chosen && <span className="hint">New ID will start {chosen.labelPrefix}-. Default loan {chosen.defaultLoanDays} days.</span>}
          </label>
          <button type="button" className="btn secondary" onClick={() => setNewType(NEW_TYPE)}><Icon name="plus" />New type</button>
          <div className="form-grid">
            <label className="field"><span>Funding pool *</span>
              <select className="input" value={pool} onChange={e => setPool(e.target.value)}>
                {pools.map(p => <option key={p.code} value={p.code}>{p.name}</option>)}
              </select>
            </label>
            <label className="field"><span>Location *</span>
              <select className="input" value={location} onChange={e => setLocation(e.target.value)}>
                {locations.map(l => <option key={l.name} value={l.name}>{l.name}</option>)}
              </select>
            </label>
          </div>
          <EquipmentDetailsFields details={details} onChange={setDetails} />
          <button className="btn" disabled={saving || !type}><Icon name="check" />{saving ? "Registering…" : "Register and print label"}</button>
        </form>
      )}
      <BottomNav />
    </main>
  );
}
