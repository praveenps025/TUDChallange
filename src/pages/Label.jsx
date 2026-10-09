import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import QRCode from "qrcode";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading } from "../components/ui";
import { post } from "../api";
import useApi from "../useApi";

// Managers only. A 50 x 25 mm label: QR code holding the item ID, with the ID
// in plain text so it can still be typed if the code won't scan.
export default function Label() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const justRegistered = params.has("new");
  const { data: item, error } = useApi(`/equipment/${encodeURIComponent(id)}/details`);
  const [svg, setSvg] = useState("");
  const [printError, setPrintError] = useState(null);

  useEffect(() => {
    if (item) QRCode.toString(item.id, { type: "svg", margin: 0, errorCorrectionLevel: "M" }).then(setSvg);
  }, [item]);

  async function print() {
    setPrintError(null);
    try {
      await post(`/equipment/${item.id}/label-printed`); // recorded in the activity log
    } catch (e) {
      setPrintError(e);
      return;
    }
    window.print();
  }

  return (
    <main className="app">
      <AppBar title="Label" />
      {!item ? <Loading error={error} /> : (
        <>
          {justRegistered && (
            <div className="callout" style={{ marginBottom: 16 }}><Icon name="check" /><span><strong>{item.id}</strong> registered and ready to issue.</span></div>
          )}
          <h1>{item.labelPrintedAt ? "Reprint label" : "Print label"}</h1>
          <p className="sub">50 × 25 mm. Stick it where it will be seen when the item is returned.</p>

          <div className="label-preview">
            <div className="qr-label">
              <span dangerouslySetInnerHTML={{ __html: svg }} />
              <div>
                <div className="id">{item.id}</div>
                <div className="what">{item.type}</div>
                <div className="owner">OT equipment · {item.poolName}</div>
              </div>
            </div>
          </div>

          {printError && <Loading error={printError} />}
          <button className="btn" onClick={print} disabled={!svg}><Icon name="print" />Print label</button>
          <Link className="btn secondary" to={`/manage/equipment/${item.id}`}>Done</Link>
          <p className="sub small">In the print dialog choose your label printer and "Actual size".</p>
        </>
      )}
      <BottomNav />
    </main>
  );
}
