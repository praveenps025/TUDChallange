import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon";
import CameraScanner from "../components/CameraScanner";
import { AppBar, Loading } from "../components/ui";
import { post } from "../api";
import useApi from "../useApi";

// Our labels hold just the equipment ID, e.g. WC-032
const LABEL = /^[A-Z0-9]{1,10}-\d+$/;

// /scan opens the scanned item. /scan?audit=1 records it on the audit walk
// instead and goes back to the walk. Scan with the camera or type the ID.
export default function Scan() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const auditing = params.has("audit");
  const { data: recent } = useApi("/equipment/recent");
  const [id, setId] = useState("");
  const [batch, setBatch] = useState(false);
  const [error, setError] = useState(null);
  const [recorded, setRecorded] = useState(null);
  const [busy, setBusy] = useState(false);

  async function openItem(label) {
    if (!auditing) return navigate(`/item/${encodeURIComponent(label)}`);
    setError(null);
    setRecorded(null);
    setBusy(true);
    try {
      const walk = await post("/audit/scan", { equipmentId: label });
      if (!batch) return navigate("/audit");
      setRecorded(`${label} recorded · ${walk.found} of ${walk.total} found`); // batch: stay and scan the next one
      setId("");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  function scanned(text) {
    const label = text.toUpperCase();
    if (!LABEL.test(label)) {
      setRecorded(null);
      setError(new Error("That QR code isn't an equipment label."));
      return;
    }
    openItem(label);
  }

  function submit(event) {
    event.preventDefault();
    if (id.trim()) openItem(id.trim().toUpperCase());
  }

  return (
    <main className="app flush">
      <AppBar title={auditing ? "Audit scan" : "Scan"} />

      <CameraScanner onScan={scanned} paused={busy} />

      {error && <div style={{ marginTop: 20 }}><Loading error={error} /></div>}
      {recorded && <div className="callout" style={{ marginTop: 20 }}><Icon name="check" />{recorded}</div>}

      <form className="tools" onSubmit={submit}>
        <input type="text" placeholder="Or type the ID, e.g. CU-024" value={id} onChange={e => setId(e.target.value)} aria-label="Equipment ID" />
        <button className="btn fit">Open</button>
      </form>

      {auditing && (
        <label className="batch">
          <span><strong>Batch scan</strong><span className="sub tight">Stay here after each item</span></span>
          <input type="checkbox" className="switch" checked={batch} onChange={e => setBatch(e.target.checked)} />
        </label>
      )}

      {recent?.length > 0 && (
        <>
          <p className="recent">Recent IDs</p>
          <div className="chips">
            {recent.map(item => (
              <Link className="chip plain" to={`/item/${item.id}`} key={item.id}
                    onClick={event => { event.preventDefault(); openItem(item.id); }}>{item.id}</Link>
            ))}
          </div>
        </>
      )}
    </main>
  );
}
