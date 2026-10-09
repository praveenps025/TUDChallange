import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, RequireAuth } from "./auth";
import Login from "./pages/Login";
import Home from "./pages/Home";
import Scan from "./pages/Scan";
import ScanResult from "./pages/ScanResult";
import ReturnCheck from "./pages/ReturnCheck";
import AuditWalk from "./pages/AuditWalk";
import Waitlist from "./pages/Waitlist";
import NewRequest from "./pages/NewRequest";
import Discharge from "./pages/Discharge";
import Insights from "./pages/Insights";
import Account from "./pages/Account";
import Users from "./pages/Users";
import Manage from "./pages/Manage";
import RegisterEquipment from "./pages/RegisterEquipment";
import Inventory from "./pages/Inventory";
import EquipmentDetail from "./pages/EquipmentDetail";
import Label from "./pages/Label";
import Approvals from "./pages/Approvals";

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />

        {/* Everyone signed in */}
        <Route element={<RequireAuth />}>
          <Route path="/" element={<Home />} />
          <Route path="/scan" element={<Scan />} />
          <Route path="/item/:id" element={<ScanResult />} />
          <Route path="/return/:id" element={<ReturnCheck />} />
          <Route path="/audit" element={<AuditWalk />} />
          <Route path="/waitlist" element={<Waitlist />} />
          <Route path="/waitlist/new" element={<NewRequest />} />
          <Route path="/discharge" element={<Discharge />} />
          <Route path="/me" element={<Account />} />
        </Route>

        {/* Managers only (the API checks the role too) */}
        <Route element={<RequireAuth role="MANAGER" />}>
          <Route path="/manage" element={<Manage />} />
          <Route path="/manage/register" element={<RegisterEquipment />} />
          <Route path="/manage/inventory" element={<Inventory />} />
          <Route path="/manage/equipment/:id" element={<EquipmentDetail />} />
          <Route path="/manage/label/:id" element={<Label />} />
          <Route path="/manage/approvals" element={<Approvals />} />
          <Route path="/insights" element={<Insights />} />
          <Route path="/users" element={<Users />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}
