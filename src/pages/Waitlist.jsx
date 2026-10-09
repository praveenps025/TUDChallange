import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading } from "../components/ui";
import { post } from "../api";
import useApi from "../useApi";

const SHOWN = 5;

export default function Waitlist() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const allocateId = params.get("allocate"); // set when issuing a scanned item
  const { data: requests, error, reload } = useApi("/waitlist");
  const { data: item, reload: reloadItem } = useApi(allocateId && `/equipment/${encodeURIComponent(allocateId)}`);
  const { data: types } = useApi("/equipment-types");
  const [type, setType] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  const filter = item ? item.type : type;
  const list = (requests || []).filter(r => !filter || r.type === filter);
  const ready = (requests || []).filter(r => r.availableNow).length;

  // The most requested type gets its own chip; the rest go under "More"
  const waiting = name => (requests || []).filter(r => r.type === name).length;
  const [main, ...more] = [...(types || [])].sort((a, b) => waiting(b.name) - waiting(a.name));
  const moreNames = more.map(t => t.name);

  async function allocate(request) {
    setSaving(true);
    setSaveError(null);
    try {
      await post(`/waitlist/${request.id}/allocate`, { equipmentId: item.id });
      navigate(`/item/${item.id}`);
    } catch (e) {
      setSaveError(e);
      setSaving(false);
      if (e.status === 409) { // the item or request changed: show the current state
        reload();
        reloadItem();
      }
    }
  }

  return (
    <main className="app">
      <AppBar title="Waitlist" sync={saving ? "saving" : "synced"} />
      {!requests ? <Loading error={error} /> : (
        <>
          <h1>{requests.length} requests waiting</h1>
          <p className="sub">Oldest first · {ready} can be allocated now</p>

          {item ? (
            <div className="callout" style={{ margin: "24px 0 20px" }}>
              <Icon name="out" />
              <span>Choose a ward for <strong>{item.id} · {item.name}</strong>.</span>
            </div>
          ) : (
            <div className="chips filters">
              <button className={`chip ${type === "" ? "on" : ""}`} onClick={() => setType("")}>All ({requests.length})</button>
              {main && <button className={`chip ${type === main.name ? "on" : ""}`} onClick={() => setType(main.name)}>{main.category}</button>}
              {more.length > 0 && (
                <select className={`chip ${moreNames.includes(type) ? "on" : ""}`} value={moreNames.includes(type) ? type : ""}
                        onChange={e => setType(e.target.value)} aria-label="More equipment types">
                  <option value="">More</option>
                  {moreNames.map(t => <option key={t}>{t}</option>)}
                </select>
              )}
            </div>
          )}

          {saveError && <Loading error={saveError} />}
          <div className="list">
            {list.slice(0, item ? list.length : SHOWN).map((r, n) => {
              const body = (
                <>
                  <span className="tile small">{n + 1}</span>
                  <span className="body">
                    <strong>{r.ward}</strong>
                    <span className="meta">{r.type} · {r.days} day{r.days === 1 ? "" : "s"} waiting</span>
                    {r.availableNow && !item && <span className="ready"><Icon name="check" />Available now</span>}
                  </span>
                  <Icon name="chevron" className="chev" />
                </>
              );
              return item
                ? <button className="row" key={r.id} disabled={saving} onClick={() => allocate(r)}>{body}</button>
                : <Link className="row" key={r.id} to="/scan">{body}</Link>;
            })}
          </div>
          {!list.length && <p className="sub">No requests waiting{filter ? ` for ${filter.toLowerCase()}s` : ""}.</p>}
          <p className="sub small">
            {item ? "Tap a ward to issue the item." : `Showing ${Math.min(SHOWN, list.length)} of ${list.length} · Scan an item to allocate it.`}
          </p>
        </>
      )}
      <BottomNav scanButton />
    </main>
  );
}
