import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router-dom';
import ProtectedRoute, { GuestRoute } from './routes/ProtectedRoute';
import PublicLayout from './layouts/PublicLayout';
import AuthLayout from './layouts/AuthLayout';
import AppLayout from './layouts/AppLayout';
import { SkeletonRows } from './components/ui/Display';

const p = (f) => lazy(f);
const Landing = p(() => import('./pages/Landing'));
const Legal = p(() => import('./pages/Legal'));
const NotFound = p(() => import('./pages/NotFound'));
const SharedProgress = p(() => import('./pages/SharedProgress'));
const ConfirmEmail = p(() => import('./pages/auth/ConfirmEmail'));
const { Login, Register, ForgotPassword, ResetPassword } = {
  Login: p(() => import('./pages/auth/Login')), Register: p(() => import('./pages/auth/Register')),
  ForgotPassword: p(() => import('./pages/auth/ForgotPassword')), ResetPassword: p(() => import('./pages/auth/ResetPassword')),
};
const Dashboard = p(() => import('./pages/Dashboard'));
const Applications = p(() => import('./pages/Applications'));
const Kanban = p(() => import('./pages/Kanban'));
const AddApplication = p(() => import('./pages/AddApplication'));
const ApplicationDetail = p(() => import('./pages/ApplicationDetail'));
const Interviews = p(() => import('./pages/Interviews'));
const Calendar = p(() => import('./pages/Calendar'));
const Analytics = p(() => import('./pages/Analytics'));
const Skills = p(() => import('./pages/Skills'));
const Goals = p(() => import('./pages/Goals'));
const Templates = p(() => import('./pages/Templates'));
const Share = p(() => import('./pages/Share'));
const Profile = p(() => import('./pages/Profile'));
const Settings = p(() => import('./pages/Settings'));

export default function App() {
  return (
    <Suspense fallback={<div className="mx-auto max-w-3xl p-8"><SkeletonRows rows={4} /></div>}>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route index element={<Landing />} />
          <Route path="legal/:doc" element={<Legal />} />
        </Route>
        <Route path="share/:token" element={<SharedProgress />} />
        <Route element={<AuthLayout />}><Route path="confirm-email" element={<ConfirmEmail />} /></Route>
        <Route element={<GuestRoute />}>
          <Route element={<AuthLayout />}>
            <Route path="login" element={<Login />} />
            <Route path="register" element={<Register />} />
            <Route path="forgot-password" element={<ForgotPassword />} />
            <Route path="reset-password" element={<ResetPassword />} />
          </Route>
        </Route>
        <Route path="app" element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route index element={<Dashboard />} />
            <Route path="applications" element={<Applications />} />
            <Route path="applications/new" element={<AddApplication />} />
            <Route path="applications/:id" element={<ApplicationDetail />} />
            <Route path="applications/:id/edit" element={<AddApplication />} />
            <Route path="board" element={<Kanban />} />
            <Route path="interviews" element={<Interviews />} />
            <Route path="calendar" element={<Calendar />} />
            <Route path="analytics" element={<Analytics />} />
            <Route path="skills" element={<Skills />} />
            <Route path="goals" element={<Goals />} />
            <Route path="templates" element={<Templates />} />
            <Route path="share" element={<Share />} />
            <Route path="profile" element={<Profile />} />
            <Route path="settings" element={<Settings />} />
          </Route>
        </Route>
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  );
}
