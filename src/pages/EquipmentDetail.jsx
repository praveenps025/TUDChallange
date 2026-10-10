import { Fragment, useState } from "react";
import { Link, useParams } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, ItemCard, Loading } from "../components/ui";
import EquipmentDetailsFields, { detailsForApi, detailsFromApi } from "../components/EquipmentForm";
import { put } from "../api";
import useApi from "../useApi";

const SHOWN = [
  ["poolName", "Funding pool"], ["weightLabel", "Weight category"], ["model", "Model"], ["serialNumber", "Serial number"], ["assetNumber", "Asset number"],
  ["assetFlag", "Asset flag"], ["size", "Size"], ["maxUserWeight", "Max user weight"], ["hc21Number", "HC21 number"],
  ["dmlNumber", "DML number"], ["therapist", "Therapist"], ["purchaseDate", "Purchased"], ["lastServiceDate", "Last service"],
  ["comments", "Comments"],
];

// Managers only: everything about one item, edit its details, print its label
export default function EquipmentDetail() {
  const { id } = useParams();
  const { data: item, error, reload } = useApi(`/equipment/${encodeURIComponent(id)}/details`);
  const { data: pools } = useApi("/pools");
  const [editing, setEditing] = useState(null);
  const [pool, setPool] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  const shown = item && { ...item, weightLabel: item.weightCategory && `${item.weightCategory} (up to ${item.weightMaxKg} kg)` };

  function edit() {
    setEditing(detailsFromApi(item));
    setPool(item.pool);
    setSaveError(null);
  }

  async function save(event) {
    event.preventDefault();
    setSaving(true);
    setSaveError(null);
    try {
      await put(`/equipment/${item.id}`, { pool, details: detailsForApi(editing) });
      setEditing(null);
      reload();
    } catch (e) {
      setSaveError(e);
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="app">
      <AppBar title="Equipment" />
      {!item ? <Loading error={error} /> : (
        <>
          <ItemCard item={item}><p className="note">{item.note}</p></ItemCard>
          {saveError && <div style={{ marginTop: 16 }}><Loading error={saveError} /></div>}

          {editing ? (
            <form className="stack" style={{ marginTop: 20 }} onSubmit={save}>
              <label className="field"><span>Funding pool</span>
                <select className="input" value={pool} onChange={e => setPool(e.target.value)}>
                  {pools?.map(p => <option key={p.code} value={p.code}>{p.name}</option>)}
                </select>
              </label>
              <EquipmentDetailsFields details={editing} onChange={setEditing} />
              <button className="btn" disabled={saving}>Save details</button>
              <button type="button" className="btn secondary" onClick={() => setEditing(null)}>Cancel</button>
            </form>
          ) : (
            <>
              <div style={{ marginTop: 20 }}>
                <Link className="btn" to={`/manage/label/${item.id}`}><Icon name="print" />{item.labelPrintedAt ? "Reprint label" : "Print label"}</Link>
                <button className="btn secondary" onClick={edit}><Icon name="edit" />Edit details</button>
                <Link className="btn secondary" to={`/item/${item.id}`}><Icon name="scan" />Open item actions</Link>
              </div>
              <div className="section"><h2 className="strong">Details</h2></div>
              <dl className="card details">
                <dt>Type</dt><dd>{item.type}</dd>
                {SHOWN.filter(([key]) => shown[key]).map(([key, label]) => (
                  <Fragment key={key}><dt>{label}</dt><dd>{shown[key]}</dd></Fragment>
                ))}
                <dt>Label</dt><dd>{item.labelPrintedAt ? `Printed ${new Date(item.labelPrintedAt).toLocaleDateString("en-IE")}` : "Not printed yet"}</dd>
              </dl>
            </>
          )}
        </>
      )}
      <BottomNav />
    </main>
  );
}
