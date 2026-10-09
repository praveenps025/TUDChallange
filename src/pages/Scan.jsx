import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar } from "../components/ui";

const RECENT_IDS = ["CU-024", "WC-018", "SB-011"];

export default function Scan() {
  const navigate = useNavigate();
  const [typing, setTyping] = useState(false);
  const [id, setId] = useState("");
  const [batch, setBatch] = useState(false);

  function open(event) {
    event.preventDefault();
    if (id.trim()) navigate(`/item/${encodeURIComponent(id.trim().toUpperCase())}`);
  }

  return (
    <main className="app flush">
      <AppBar title="Scan" />

      {/* Static camera preview: tapping it "scans" the label in view */}
      <Link className="camera" to="/item/CU-024" aria-label="Scan CU-024">
        <span className="tag">CAMERA · STATIC PREVIEW</span>
        <span className="chair" /><span className="bench" />
        <span className="label low"><Icon name="qr" />CU-024</span>
        <span className="frame" />
        <span className="hint">Align the equipment label in the square</span>
      </Link>

      {typing ? (
        <form className="tools" onSubmit={open}>
          <input type="text" autoFocus placeholder="Equipment ID, e.g. CU-024" value={id} onChange={e => setId(e.target.value)} />
          <button className="btn fit">Open</button>
        </form>
      ) : (
        <div className="tools">
          <button className="btn secondary torch" aria-label="Torch"><Icon name="torch" /></button>
          <button className="btn secondary" onClick={() => setTyping(true)}><Icon name="keyboard" />Enter ID</button>
        </div>
      )}

      <label className="batch">
        <span><strong>Batch scan</strong><span className="sub tight">Keep scanning after each item</span></span>
        <input type="checkbox" className="switch" checked={batch} onChange={e => setBatch(e.target.checked)} />
      </label>

      <p className="recent">Recent IDs</p>
      <div className="chips">
        {RECENT_IDS.map(recent => <Link className="chip plain" to={`/item/${recent}`} key={recent}>{recent}</Link>)}
      </div>
    </main>
  );
}
