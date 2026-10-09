import { Link } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading } from "../components/ui";
import useApi from "../useApi";

function greeting() {
  const hour = new Date().getHours();
  return hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
}

export default function Home() {
  const { data, error } = useApi("/home");
  const today = new Date().toLocaleDateString("en-GB", { weekday: "long", day: "numeric", month: "long" });

  return (
    <main className="app">
      <AppBar title="Home" />
      {!data ? <Loading error={error} /> : (
        <>
          <h1>{greeting()}, {data.user}</h1>
          <p className="sub">{data.team} · {today}</p>

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
