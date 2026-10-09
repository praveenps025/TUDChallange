import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { Navigate, Outlet, useLocation, useNavigate } from "react-router-dom";
import { clearSession, get, getSession, onUnauthorized, post, saveSession } from "./api";

// Who is signed in on this tab. user is undefined while the saved session is
// being checked on page load, null when signed out.
const AuthContext = createContext(null);

export const ROLE_NAMES = { MANAGER: "Manager", OT_STAFF: "OT staff" };

export function AuthProvider({ children }) {
  const navigate = useNavigate();
  const [user, setUser] = useState(getSession() ? undefined : null);
  const [notice, setNotice] = useState(null);

  // Restore the session after a reload
  useEffect(() => {
    if (getSession()) get("/auth/me").then(setUser, () => setUser(null));
  }, []);

  // A 401 anywhere ends the session and sends the user to the login screen
  useEffect(() => onUnauthorized(message => {
    setUser(null);
    setNotice(message);
  }), []);

  // Sign out when the token expires, even if the screen is left open
  useEffect(() => {
    const session = getSession();
    if (!user || !session) return;
    const timer = setTimeout(() => {
      clearSession();
      setUser(null);
      setNotice("Your session has expired. Please sign in again.");
    }, Date.parse(session.expiresAt) - Date.now());
    return () => clearTimeout(timer);
  }, [user]);

  const login = useCallback(async (username, password) => {
    const result = await post("/auth/login", { username, password });
    saveSession(result);
    setNotice(null);
    setUser(result.user);
  }, []);

  // Shared devices: clear everything and go straight back to sign-in for the next person
  const logout = useCallback(() => {
    clearSession();
    setUser(null);
    setNotice(null);
    navigate("/login", { replace: true });
  }, [navigate]);

  return <AuthContext.Provider value={{ user, notice, login, logout }}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);

// Route guard: <Route element={<RequireAuth role="MANAGER" />}>
export function RequireAuth({ role }) {
  const { user } = useAuth();
  const location = useLocation();

  if (user === undefined) return <main className="app"><p className="sub">Loading…</p></main>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (role && user.role !== role) return <Navigate to="/" replace />;
  return <Outlet />;
}

export function firstName(user) {
  return user?.name.split(" ")[0] ?? "";
}

export function initials(user) {
  return (user?.name ?? "").split(" ").filter(Boolean).slice(0, 2).map(part => part[0].toUpperCase()).join("");
}
