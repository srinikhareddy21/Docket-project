import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { WifiOff } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { Button } from '../components/ui/Button';
import { Logo } from '../components/ui/Logo';

export function FullScreenMessage({ children }) {
  return <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-6 text-center"><Logo />{children}</div>;
}

export default function ProtectedRoute() {
  const { status, restore } = useAuth();
  const loc = useLocation();
  if (status === 'loading') return <FullScreenMessage><p className="text-sm text-fg-muted" role="status">Checking your session…</p></FullScreenMessage>;
  if (status === 'unreachable') return (
    <FullScreenMessage>
      <WifiOff className="text-fg-subtle" />
      <h1 className="text-xl font-semibold">Cannot reach the DOCKET server</h1>
      <p className="max-w-sm text-sm text-fg-muted">Your workspace needs the backend API. Start it, or check <code>VITE_API_BASE_URL</code>, then retry.</p>
      <Button onClick={restore}>Retry</Button>
    </FullScreenMessage>
  );
  if (status !== 'authenticated') return <Navigate to="/login" replace state={{ from: loc.pathname + loc.search }} />;
  return <Outlet />;
}

export function GuestRoute() {
  const { status } = useAuth();
  if (status === 'authenticated') return <Navigate to="/app" replace />;
  return <Outlet />;
}
