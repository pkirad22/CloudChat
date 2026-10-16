import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";

import Landing from "./pages/Landing";
import Login from "./pages/Login";
import Register from "./pages/Register";
import VerifyOTP from "./pages/VerifyOTP";
import AdminLogin from "./pages/AdminLogin";
import Dashboard from "./pages/Dashboard";
import AdminDashboard from "./admin/AdminDashboard";
import Chat from "./pages/Chat";
import FileSharing from "./pages/FileSharing";
import Groups from "./pages/Groups";
import GroupChat from "./pages/GroupChat";
import GroupFiles from "./pages/GroupFiles";
import UserProfile from "./pages/UserProfile";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/verify-otp" element={<VerifyOTP />} />

        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/profile" element={<UserProfile />} />

        <Route path="/admin/login" element={<AdminLogin />} />
        <Route path="/admin/dashboard" element={<AdminDashboard />} />

        <Route path="/chat" element={<Chat />} />
        <Route path="/file-sharing" element={<FileSharing />} />
        <Route path="/groups" element={<Groups />} />

        <Route path="/group-chat" element={<GroupChat />} />
        <Route path="/group-files" element={<GroupFiles />} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
