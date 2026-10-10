import { useState } from "react";
import { Link } from "react-router-dom";
import { AppBar, BottomNav, Loading } from "../components/ui";
import useApi from "../useApi";

const PARTS = [["available", "Available"], ["issued", "Issued"], ["check", "Check / missing"]];
const PERIODS = [["this-month", "This month"], ["last-month", "Last month"], ["this-year", "This year"]];

// Managers only. Stock and waitlist figures are as of now; loans, returns and
// on-time returns cover the chosen period.
export default function Insights() {
  const [period, setPeriod] = useState("this-month");
  const { data, error } = useApi(`/insights?period=${period}`);
  const periodText = PERIODS.find(([value]) => value === period)[1].toLowerCase();

  return (
    <main className="app">
      <AppBar title="Insights" />
      <div className="overview">
        <h2>{data ? `${data.month} overview` : "Overview"}</h2>
        <select aria-label="Period" value={period} onChange={e => setPeriod(e.target.value)}>
          {PERIODS.map(([value, text]) => <option key={value} value={value}>{text}</option>)}
        </select>
      </div>

      {!data ? <Loading error={error} /> : (
        <>
          <div className="section" style={{ marginTop: 0 }}><h2 className="strong">Right now</h2></div>
          <div className="tiles">
            <div className="card"><span className="n">{data.tracked}</span><strong>Tracked</strong><span className="sub">All equipment</span></div>
            <Link className="card" to="/audit"><span className="n">{data.unaccounted}</span><strong>Unaccounted</strong><span className="sub">Review in audit</span></Link>
            <Link className="card" to="/waitlist"><span className="n">{data.waitlists}</span><strong>Waitlists</strong><span className="sub">{data.readyToAllocate} ready to allocate</span></Link>
          </div>

          <div className="section"><h2 className="strong">Loans {periodText}</h2></div>
          <div className="tiles">
            <div className="card"><span className="n">{data.issued}</span><strong>Issued</strong><span className="sub">Loans started</span></div>
            <div className="card"><span className="n">{data.returned}</span><strong>Returned</strong><span className="sub">Items back</span></div>
            <div className="card"><span className="n">{data.onTime == null ? "–" : `${data.onTime}%`}</span><strong>On time</strong><span className="sub">Returned by the due date</span></div>
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
