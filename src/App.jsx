import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, RequireAuth } from "./auth";
import Login from "./pages/Login";
import Home from "./pages/Home";
import Scan from "./pages/Scan";
import ScanResult from "./pages/ScanResult";
import ReturnCheck from "./pages/ReturnCheck";
import AuditWalk from "./pages/AuditWalk";
import Waitlist from "./pages/Waitlist";
import Discharge from "./pages/Discharge";
import Insights from "./pages/Insights";
import Account from "./pages/Account";
import Users from "./pages/Users";

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route element={<RequireAuth />}>
          <Route path="/" element={<Home />} />
          <Route path="/scan" element={<Scan />} />
          <Route path="/item/:id" element={<ScanResult />} />
          <Route path="/return/:id" element={<ReturnCheck />} />
          <Route path="/audit" element={<AuditWalk />} />
          <Route path="/waitlist" element={<Waitlist />} />
          <Route path="/discharge" element={<Discharge />} />
          <Route path="/insights" element={<Insights />} />
          <Route path="/me" element={<Account />} />
        </Route>
        <Route element={<RequireAuth role="MANAGER" />}>
          <Route path="/users" element={<Users />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}
