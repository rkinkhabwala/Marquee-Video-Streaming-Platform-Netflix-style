import { Navigate, Outlet } from 'react-router-dom';
import { useActiveProfile, useSession } from '../auth/useSession';
import { NavBar } from '../components/NavBar';
import { TitleModal } from '../components/TitleModal';
import { useTitleModal } from '../components/useTitleModal';

export function RequireSession() {
  return useSession() ? <Outlet /> : <Navigate to="/login" replace />;
}

export function RequireProfile() {
  return useActiveProfile() ? <Outlet /> : <Navigate to="/profiles" replace />;
}

export function RequireAdmin() {
  return useSession()?.role === 'ADMIN' ? <Outlet /> : <Navigate to="/" replace />;
}

/** Pages with the top navigation and the title detail modal (opened with ?title=). */
export function AppLayout() {
  const modal = useTitleModal();
  const profile = useActiveProfile();
  return (
    <>
      <NavBar />
      <Outlet />
      {profile && modal.titleId !== null && <TitleModal key={modal.titleId} titleId={modal.titleId} onClose={modal.close} />}
    </>
  );
}
