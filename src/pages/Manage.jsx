import { Link } from "react-router-dom";
import Icon from "../components/Icon";
import { AppBar, BottomNav } from "../components/ui";
import useApi from "../useApi";

// Managers only: everything staff don't do, in one place
export default function Manage() {
  const { data: approvals } = useApi("/discharge/overrides");
  const items = [
    ["/manage/register", "plus", "Register equipment", "Add a new item and print its QR label"],
    ["/manage/inventory", "box", "Inventory", "Find any item, edit details, reprint a label"],
    ["/manage/approvals", "shield", "Discharge approvals", "Overrides requested by staff", approvals?.length],
    ["/insights", "chart", "Insights", "Stock, waitlists and returns this month"],
    ["/audit", "route", "Audit walk", "Check what is where"],
    ["/users", "users", "Users", "Add staff, change roles, reset passwords"],
  ];

  return (
    <main className="app">
      <AppBar title="Manage" />
      <div className="list">
        {items.map(([to, icon, title, detail, count]) => (
          <Link className="row" to={to} key={to}>
            <span className="tile"><Icon name={icon} /></span>
            <span className="body"><strong>{title}</strong><span className="meta">{detail}</span></span>
            {count > 0 && <span className="count">{count}</span>}
            <Icon name="chevron" className="chev" />
          </Link>
        ))}
      </div>
      <BottomNav scanButton />
    </main>
  );
}
