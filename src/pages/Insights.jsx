import { Link } from "react-router-dom";
import { AppBar, BottomNav, Loading } from "../components/ui";
import useApi from "../useApi";

const PARTS = [["available", "Available"], ["issued", "Issued"], ["check", "Check / missing"]];

export default function Insights() {
  const { data, error } = useApi("/insights");

  return (
    <main className="app">
      <AppBar title="Insights" />
      {!data ? <Loading error={error} /> : (
        <>
          <div className="overview">
            <h2>{data.month} overview</h2>
          </div>

          <div className="tiles">
            <div className="card"><span className="n">{data.tracked}</span><strong>Tracked</strong><span className="sub">All equipment</span></div>
            <Link className="card" to="/audit"><span className="n">{data.unaccounted}</span><strong>Unaccounted</strong><span className="sub">Review in audit</span></Link>
            <Link className="card" to="/waitlist"><span className="n">{data.waitlists}</span><strong>Waitlists</strong><span className="sub">{data.readyToAllocate} ready to allocate</span></Link>
            <div className="card"><span className="n">{data.onTime == null ? "–" : `${data.onTime}%`}</span><strong>On time</strong><span className="sub">Returns this month</span></div>
          </div>

          <div className="section"><h2 className="strong">By equipment category</h2></div>
          <div className="legend">
            {PARTS.map(([key, text]) => <span key={key} style={{ "--dot": `var(--c-${key})` }}>{text}</span>)}
          </div>
          <div className="list">
            {data.categories.map(c => (
              <div className="card category" key={c.name}>
                <div className="head"><h3>{c.name}</h3>{c.advice && <span className="pill info square">{c.advice}</span>}</div>
                <div className="bar">
                  {PARTS.map(([key]) => <span key={key} title={`${c[key]} ${key}`} style={{ flex: c[key], "--dot": `var(--c-${key})` }} />)}
                </div>
                <span className="sub">{c.available} available · {c.waiting} request{c.waiting === 1 ? "" : "s"} waiting</span>
              </div>
            ))}
          </div>
        </>
      )}
      <BottomNav scanButton />
    </main>
  );
}
