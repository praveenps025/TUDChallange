import { useState } from "react";
import { Link } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading, StatusPill } from "../components/ui";
import useApi from "../useApi";

const STATUSES = [["", "All"], ["available", "Available"], ["issued", "Issued"], ["inspect", "Inspect"], ["missing", "Missing"]];
const PAGE = 20;

// Managers only. Search by ID, name, asset number, serial number or model,
// e.g. to reprint a label that has worn off.
export default function Inventory() {
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [size, setSize] = useState(PAGE);
  const params = new URLSearchParams({ size, ...(query && { search: query }), ...(status && { status }) });
  const { data, error } = useApi(`/equipment?${params}`);

  function find(event) {
    event.preventDefault();
    setQuery(search.trim());
    setSize(PAGE);
  }

  return (
    <main className="app">
      <AppBar title="Inventory" />
      <Link className="btn" to="/manage/register"><Icon name="plus" />Register equipment</Link>

      <form className="search" onSubmit={find}>
        <Icon name="search" />
        <input type="text" placeholder="ID, name, asset or serial number" value={search} onChange={e => setSearch(e.target.value)} aria-label="Search equipment" />
      </form>
      <div className="chips" style={{ marginBottom: 20 }}>
        {STATUSES.map(([value, text]) => (
          <button key={value} className={`chip ${status === value ? "on" : ""}`} onClick={() => { setStatus(value); setSize(PAGE); }}>{text}</button>
        ))}
      </div>

      {!data ? <Loading error={error} /> : (
        <>
          <p className="sub" style={{ marginBottom: 12 }}>{data.total} item{data.total === 1 ? "" : "s"}</p>
          <div className="list">
            {data.items.map(item => (
              <Link className="row" to={`/manage/equipment/${item.id}`} key={item.id}>
                <span className="tile"><Icon name={item.category === "Wheelchairs" ? "wheelchair" : "layers"} /></span>
                <span className="body">
                  <strong>{item.id} · {item.name}</strong>
                  <span className="meta">{item.location}</span>
                  <span className="tags"><StatusPill status={item.status} /></span>
                </span>
                <Icon name="chevron" className="chev" />
              </Link>
            ))}
          </div>
          {data.items.length < data.total && (
            <button className="btn secondary" style={{ marginTop: 16 }} onClick={() => setSize(size + PAGE)}>Show more</button>
          )}
        </>
      )}
      <BottomNav />
    </main>
  );
}
