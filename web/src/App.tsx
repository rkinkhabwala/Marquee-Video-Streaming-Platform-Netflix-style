import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { HomePage } from './pages/HomePage';
import { AppLayout, RequireAdmin, RequireProfile, RequireSession } from './pages/Layout';
import { LoginPage } from './pages/LoginPage';
import { ProfilesPage } from './pages/ProfilesPage';
import { SearchPage } from './pages/SearchPage';

// The player (hls.js) and admin screens are split out of the main bundle.
const WatchPage = lazy(() => import('./pages/WatchPage').then((m) => ({ default: m.WatchPage })));
const AdminPage = lazy(() => import('./pages/AdminPage').then((m) => ({ default: m.AdminPage })));

export function App() {
  return (
    <Suspense fallback={<p className="page-status">Loading…</p>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireSession />}>
          <Route path="/profiles" element={<ProfilesPage />} />
          <Route element={<RequireProfile />}>
            <Route path="/watch/:assetId" element={<WatchPage />} />
          </Route>
          <Route element={<AppLayout />}>
            <Route element={<RequireProfile />}>
              <Route path="/" element={<HomePage />} />
              <Route path="/search" element={<SearchPage />} />
            </Route>
            <Route element={<RequireAdmin />}>
              <Route path="/admin" element={<AdminPage />} />
            </Route>
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
