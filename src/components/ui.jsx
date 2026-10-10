import { Link, NavLink } from "react-router-dom";
import Icon from "./Icon";
import { firstName, initials, useAuth } from "../auth";

// The signed-in name is always visible, so on shared devices staff can see whose session it is
export function AppBar({ title }) {
  const { user } = useAuth();
  return (
    <header className="appbar">
      <Link className="mark" to="/" aria-label="Home"><Icon name="scan" /></Link>
      <div>
        <span className="eyebrow">EQUIPTRACK OT{user && ` · ${firstName(user).toUpperCase()}`}</span>
        <span className="page">{title}</span>
      </div>
      {user && <Link className="avatar" to="/me" aria-label={`Signed in as ${user.name}. Account and sign out`}>{initials(user)}</Link>}
    </header>
  );
}

const STATUS = {
  available: ["check", "Available"],
  issued: ["out", "Issued"],
  inspect: ["clipboard", "Needs Inspection"],
  missing: ["alert", "Missing"],
};

export function StatusPill({ status }) {
  const [icon, text] = STATUS[status];
  return <span className={`pill ${status}`}><Icon name={icon} />{text}</span>;
}

// Staff get the audit walk; managers get Manage (registration, inventory,
// approvals, insights, users) in its place
const TABS = {
  OT_STAFF: [["/", "home", "Home"], ["/waitlist", "list", "Waitlist"], ["/discharge", "clipboard", "Discharge"], ["/audit", "route", "Audit"]],
  MANAGER: [["/", "home", "Home"], ["/waitlist", "list", "Waitlist"], ["/discharge", "clipboard", "Discharge"], ["/manage", "grid", "Manage"]],
};

export function BottomNav({ scanButton = false }) {
  const { user } = useAuth();
  const tabs = TABS[user?.role] ?? TABS.OT_STAFF;
  return (
    <nav className="tabs">
      <div className="inner">
        {tabs.map(([to, icon, text]) => (
          <NavLink key={to} to={to} end={to === "/"} className={({ isActive }) => (isActive ? "on" : "")}>
            <Icon name={icon} />{text}
          </NavLink>
        ))}
        {scanButton && <Link className="fab" to="/scan" aria-label="Scan"><Icon name="scan" /></Link>}
      </div>
    </nav>
  );
}

export function ItemCard({ item, children }) {
  return (
    <div className="card item">
      <div className="top">
        <span className="tile"><Icon name={item.category === "Wheelchairs" ? "wheelchair" : "layers"} /></span>
        <div><div className="name">{item.name}</div><div className="sub tight">{item.id} · {item.category}</div></div>
      </div>
      <div className="status"><StatusPill status={item.status} /><span>{item.location}</span></div>
      {children}
    </div>
  );
}

// Shown while a screen loads or when the API call fails
export function Loading({ error }) {
  if (error) return <div className="callout error"><Icon name="alert" />{error.message}</div>;
  return <p className="sub">Loading…</p>;
}
