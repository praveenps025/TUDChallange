import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, ItemCard, Loading } from "../components/ui";
import { post } from "../api";
import useApi from "../useApi";

export default function ReturnCheck() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: item, error, reload } = useApi(`/equipment/${encodeURIComponent(id)}`);
  const { data: waitlist, reload: reloadWaitlist } = useApi("/waitlist");
  const [clean, setClean] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  // Oldest request for the same kind of equipment
  const next = item && waitlist?.find(r => r.type === item.type);

  async function finish(allocate) {
    setSaving(true);
    setSaveError(null);
    try {
      // One call when allocating, so the item is never left returned but unallocated
      if (clean && allocate && next) await post(`/equipment/${item.id}/return-and-allocate`, { requestId: next.id });
      else await post(`/equipment/${item.id}/return`, { clean });
      navigate(`/item/${item.id}`);
    } catch (e) {
      setSaveError(e);
      setSaving(false);
      if (e.status === 409) { // someone else got there first: show the current state
        reload();
        reloadWaitlist();
      }
    }
  }

  return (
    <main className="app">
      <AppBar title="Return check" sync={saving ? "saving" : "synced"} />
      <div className="steps"><span>RETURN · CHECK · ALLOCATE</span><span>2 of 3</span></div>

      {!item ? <Loading error={error} /> : item.status !== "issued" ? (
        <>
          <ItemCard item={item} />
          <p className="sub" style={{ margin: "20px 0" }}>This item isn't issued, so there is nothing to return.</p>
          <Link className="btn" to={`/item/${item.id}`}>Back to item</Link>
        </>
      ) : (
        <>
          <ItemCard item={item} />

          <h1 style={{ marginTop: 28 }}>Clean and undamaged?</h1>
          <p className="sub">Check the cover, seams and pressure surface.</p>
          <div className="answer">
            <button className={`chip ${clean ? "on" : ""}`} onClick={() => setClean(true)}><Icon name={clean ? "check" : "circle"} />Yes</button>
            <button className={`chip ${clean ? "" : "on"}`} onClick={() => setClean(false)}><Icon name={clean ? "circle" : "check"} />No</button>
          </div>

          {clean && next && (
            <div className="callout next">
              <Icon name="list" />
              <strong>Next on waitlist: {next.ward}, waiting {next.days} days</strong>
              <span>A {next.type.toLowerCase()} is requested. Allocate this returned item now.</span>
            </div>
          )}
          {!clean && (
            <div className="callout next">
              <Icon name="clipboard" />
              <strong>Send for inspection</strong>
              <span>The item goes back to the OT store and can't be issued until it is inspected.</span>
            </div>
          )}

          {saveError && <div style={{ marginTop: 20 }}><Loading error={saveError} /></div>}
          <div className="actions">
            {clean && next ? (
              <>
                <button className="btn" disabled={saving} onClick={() => finish(true)}>Allocate to {next.ward}</button>
                <button className="btn secondary" disabled={saving} onClick={() => finish(false)}>Skip</button>
              </>
            ) : (
              <button className="btn" disabled={saving} onClick={() => finish(false)}>
                {clean ? "Return to stock" : "Return for inspection"}
              </button>
            )}
          </div>
        </>
      )}
      <BottomNav />
    </main>
  );
}
