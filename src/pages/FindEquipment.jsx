import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav, Loading } from "../components/ui";
import useApi from "../useApi";

// Staff and managers: is there one I can take now, and where is it?
// Filters live in the URL (/find?category=Wheelchairs&weight=Overweight) so
// the Home search bar can link straight here.
export default function FindEquipment() {
  const [params, setParams] = useSearchParams();
  const category = params.get("category") || "";
  const weight = params.get("weight") || "";
  const { data: categories } = useApi("/categories");
  const { data: weights } = useApi("/weight-categories");
  const query = new URLSearchParams(Object.entries({ category, weight }).filter(([, v]) => v));
  const { data: result, error } = useApi(`/equipment/available?${query}`);

  function update(changes) {
    const next = { category, weight, ...changes };
    setParams(Object.fromEntries(Object.entries(next).filter(([, v]) => v)), { replace: true });
  }

  // The request form needs one type: the chosen category's, if it has just one
  const chosen = categories?.find(c => c.name === category);
  const requestType = chosen?.types.length === 1 ? chosen.types[0] : "";

  return (
    <main className="app">
      <AppBar title="Find equipment" />

      <p className="hint" style={{ margin: "0 0 8px" }}>Category</p>
      <div className="chips" style={{ marginBottom: 14 }}>
        <button className={`chip ${!category ? "on" : ""}`} onClick={() => update({ category: "" })}>All</button>
        {categories?.map(c => (
          <button key={c.name} className={`chip ${category === c.name ? "on" : ""}`} onClick={() => update({ category: c.name })}>{c.name}</button>
        ))}
      </div>

      <p className="hint" style={{ margin: "0 0 8px" }}>Patient weight</p>
      <div className="chips" style={{ marginBottom: 24 }}>
        <button className={`chip ${!weight ? "on" : ""}`} onClick={() => update({ weight: "" })}>Any</button>
        {weights?.map(w => (
          <button key={w.name} className={`chip ${weight === w.name ? "on" : ""}`} onClick={() => update({ weight: w.name })}>
            {w.name} <span className="hint">≤{w.maxKg} kg</span>
          </button>
        ))}
      </div>

      {!result ? <Loading error={error} /> : result.items.length ? (
        <>
          <div className="section" style={{ marginTop: 0 }}><h2 className="strong">{result.items.length} available now</h2></div>
          <div className="list">
            {result.items.map(item => (
              <Link className="row" to={`/item/${item.id}`} key={item.id}>
                <span className="tile"><Icon name={item.category === "Wheelchairs" ? "wheelchair" : "layers"} /></span>
                <span className="body">
                  <strong>{item.type} · {item.name}</strong>
                  <span className="meta">{item.id} · {item.location}</span>
                  <span className="tags">
                    {item.weightCategory
                      ? <span className="pill info">{item.weightCategory} · up to {item.maxKg} kg</span>
                      : <span className="pill routine">Weight not recorded</span>}
                  </span>
                </span>
                <Icon name="chevron" className="chev" />
              </Link>
            ))}
          </div>
        </>
      ) : (
        <div className="card" style={{ textAlign: "center", padding: 28 }}>
          <Icon name="search" style={{ width: 36, height: 36, color: "var(--muted)" }} />
          <h2 style={{ margin: "12px 0 6px" }}>Oops, nothing available</h2>
          <p className="sub" style={{ marginBottom: 20 }}>
            None can be taken right now. Add the patient to the waitlist and they'll be next when one comes back.
          </p>
          <Link className="btn" to={`/waitlist/new${requestType ? `?type=${encodeURIComponent(requestType)}` : ""}`}><Icon name="plus" />Add to waitlist</Link>
        </div>
      )}
      <BottomNav scanButton />
    </main>
  );
}
