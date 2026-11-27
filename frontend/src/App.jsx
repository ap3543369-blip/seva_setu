import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell, PublicLayout, RequireLogin, RequireRole } from './components/Layouts.jsx';
import ModuleGate from './components/ModuleGate.jsx';
import Hub from './pages/Hub.jsx';
import AdminModules from './pages/admin/AdminModules.jsx';
import AdminMedi from './pages/admin/AdminMedi.jsx';
import AdminReports from './pages/admin/AdminReports.jsx';
import AdminApprovals from './pages/admin/AdminApprovals.jsx';
import NgoRegister from './pages/NgoRegister.jsx';
import BookBrowse from './pages/books/BookBrowse.jsx';
import BookDonate from './pages/books/BookDonate.jsx';
import MyBooks from './pages/books/MyBooks.jsx';
import BookRequests from './pages/books/BookRequests.jsx';
import BookDetail from './pages/books/BookDetail.jsx';
import MediHome from './pages/medi/MediHome.jsx';
import MediDonate from './pages/medi/MediDonate.jsx';
import MediMine from './pages/medi/MediMine.jsx';
import MediBrowse from './pages/medi/MediBrowse.jsx';
import MediRequests from './pages/medi/MediRequests.jsx';
import EcoHome from './pages/eco/EcoHome.jsx';
import EcoPickups from './pages/eco/EcoPickups.jsx';
import EcoRecords from './pages/eco/EcoRecords.jsx';
import EcoForecast from './pages/eco/EcoForecast.jsx';
import Landing from './pages/Landing.jsx';
import AuthPage from './pages/AuthPage.jsx';
import Impact from './pages/Impact.jsx';
import Ngos from './pages/Ngos.jsx';
import Contact from './pages/Contact.jsx';
import DonationDetail from './pages/DonationDetail.jsx';
import Notifications from './pages/Notifications.jsx';
import Profile from './pages/Profile.jsx';
import Settings from './pages/Settings.jsx';
import DonorDashboard from './pages/donor/DonorDashboard.jsx';
import Donate from './pages/donor/Donate.jsx';
import MyDonations from './pages/donor/MyDonations.jsx';
import Rewards from './pages/donor/Rewards.jsx';
import NgoDashboard from './pages/ngo/NgoDashboard.jsx';
import Browse from './pages/ngo/Browse.jsx';
import Requests from './pages/ngo/Requests.jsx';
import AdminOverview from './pages/admin/AdminOverview.jsx';
import AdminUsers from './pages/admin/AdminUsers.jsx';
import AdminDonations from './pages/admin/AdminDonations.jsx';
import AdminMessages from './pages/admin/AdminMessages.jsx';

export default function App() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route path="/" element={<Landing />} />
        <Route path="/impact" element={<Impact />} />
        <Route path="/ngos" element={<Ngos />} />
        <Route path="/contact" element={<Contact />} />
        <Route path="/donation/:id" element={<DonationDetail />} />
        <Route element={<RequireLogin />}>
          <Route path="/hub" element={<Hub />} />
        </Route>
      </Route>

      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/signup" element={<AuthPage mode="signup" />} />
      <Route path="/register/ngo" element={<NgoRegister />} />

      <Route path="/donor" element={<RequireRole role="donor" />}>
        <Route index element={<DonorDashboard />} />
        <Route path="donate" element={<Donate />} />
        <Route path="donations" element={<MyDonations />} />
        <Route path="rewards" element={<Rewards />} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="profile" element={<Profile />} />
        <Route path="settings" element={<Settings />} />
      </Route>

      <Route path="/ngo" element={<RequireRole role="ngo" />}>
        <Route index element={<NgoDashboard />} />
        <Route path="browse" element={<Browse />} />
        <Route path="requests" element={<Requests />} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="profile" element={<Profile />} />
        <Route path="settings" element={<Settings />} />
      </Route>

      <Route path="/admin" element={<RequireRole role="admin" />}>
        <Route index element={<AdminOverview />} />
        <Route path="users" element={<AdminUsers only="all" />} />
        <Route path="ngos" element={<AdminUsers only="ngo" />} />
        <Route path="donations" element={<AdminDonations />} />
        <Route path="messages" element={<AdminMessages />} />
        <Route path="modules" element={<AdminModules />} />
        <Route path="medishare" element={<AdminMedi />} />
        <Route path="reports" element={<AdminReports />} />
        <Route path="approvals" element={<AdminApprovals />} />
        <Route path="settings" element={<Settings />} />
      </Route>

      {/* shared shell: every logged-in role; module pages are gated by the backend's module permissions */}
      <Route element={<AppShell />}>
        <Route path="/notifications" element={<Notifications />} />
        <Route path="/profile" element={<Profile />} />
        <Route path="/settings" element={<Settings />} />
        <Route element={<ModuleGate module="bookshare" />}>
          <Route path="/books" element={<BookBrowse />} />
          <Route path="/books/donate" element={<BookDonate />} />
          <Route path="/books/mine" element={<MyBooks />} />
          <Route path="/books/requests" element={<BookRequests />} />
          <Route path="/books/:id" element={<BookDetail />} />
        </Route>
        <Route element={<ModuleGate module="medishare" />}>
          <Route path="/medishare" element={<MediHome />} />
          <Route path="/medishare/donate" element={<MediDonate />} />
          <Route path="/medishare/mine" element={<MediMine />} />
          <Route path="/medishare/browse" element={<MediBrowse />} />
          <Route path="/medishare/requests" element={<MediRequests />} />
        </Route>
        <Route element={<ModuleGate module="ecoai" />}>
          <Route path="/ecoai" element={<EcoHome />} />
          <Route path="/ecoai/pickups" element={<EcoPickups />} />
          <Route path="/ecoai/records" element={<EcoRecords />} />
          <Route path="/ecoai/forecast" element={<EcoForecast />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
