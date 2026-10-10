import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading } from "../components/ui";
import { firstName, useAuth } from "../auth";
import useApi from "../useApi";

function greeting() {
  const hour = new Date().getHours();
  return hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
}

export default function Home() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { data: categories } = useApi("/categories");
  const { data: weights } = useApi("/weight-categories");
  const [category, setCategory] = useState("");
  const [weight, setWeight] = useState("");
  const { data, error } = useApi("/home");
  const today = new Date().toLocaleDateString("en-GB", { weekday: "long", day: "numeric", month: "long" });

  function findEquipment(event) {
    event.preventDefault();
    const query = new URLSearchParams(Object.entries({ category, weight }).filter(([, v]) => v));
    navigate(`/find${query.size ? `?${query}` : ""}`);
  }

  return (
    <main className="app">
      <AppBar title="Home" />
      {!data ? <Loading error={error} /> : (
        <>
          <h1>{greeting()}, {firstName(user)}</h1>
          <p className="sub">{data.team} · {today}</p>

          <form className="card finder" onSubmit={findEquipment}>
            <strong>Find equipment</strong>
            <div className="form-grid">
              <select className="input" value={category} onChange={e => setCategory(e.target.value)} aria-label="Equipment category">
                <option value="">All categories</option>
                {categories?.map(c => <option key={c.name} value={c.name}>{c.name}</option>)}
              </select>
              <select className="input" value={weight} onChange={e => setWeight(e.target.value)} aria-label="Patient weight">
                <option value="">Any weight</option>
                {weights?.map(w => <option key={w.name} value={w.name}>{w.name} (up to {w.maxKg} kg)</option>)}
              </select>
            </div>
            <button className="btn"><Icon name="search" />Find</button>
          </form>

          <div className="section"><h2>To do</h2><span className="muted">{data.tasks.length} tasks</span></div>
          <div className="list">
            {data.tasks.map(task => (
              <Link className="row" to={task.to} key={task.title}>
                <span className="tile"><Icon name={task.icon} /></span>
                <span className="body"><strong>{task.title}</strong><span className="meta">{task.detail}</span></span>
                <Icon name="chevron" className="chev" />
              </Link>
            ))}
          </div>

          <div className="section"><h2>Recent activity</h2><span className="muted">Today</span></div>
          <div className="card rows">
            {data.activity.map((a, n) => (
              <div className="row" key={n}>
                <Icon name={a.icon} />
                <span className="body"><strong>{a.title}</strong><span className="meta">{a.detail}</span></span>
                <span className="time">{a.time}</span>
              </div>
            ))}
          </div>
        </>
      )}
      <BottomNav scanButton />
    </main>
  );
}
