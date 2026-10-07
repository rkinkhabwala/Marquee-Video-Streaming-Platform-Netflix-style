import { useQueryClient } from '@tanstack/react-query';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { logout } from '../api/endpoints';
import { setProfile, setSession } from '../auth/session';
import { useActiveProfile, useSession } from '../auth/useSession';

export function NavBar() {
  const session = useSession();
  const profile = useActiveProfile();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const signOut = async () => {
    try {
      await logout();
    } catch {
      // The local session is cleared regardless.
    }
    setSession(null);
    queryClient.clear();
    navigate('/login');
  };

  const switchProfile = () => {
    setProfile(null);
    queryClient.clear();
    navigate('/profiles');
  };

  return (
    <header className="navbar">
      <Link to={profile ? '/' : '/admin'} className="navbar__logo">
        MARQUEE
      </Link>
      <nav className="navbar__links">
        {profile && (
          <>
            <NavLink to="/" end>
              Home
            </NavLink>
            <NavLink to="/search">Search</NavLink>
          </>
        )}
        {session?.role === 'ADMIN' && <NavLink to="/admin">Admin</NavLink>}
      </nav>
      <div className="navbar__account">
        {profile && (
          <button type="button" className="navbar__profile" onClick={switchProfile} title="Switch profile">
            <span className="avatar avatar--small" data-kids={profile.isKids || undefined}>
              {profile.name.charAt(0).toUpperCase()}
            </span>
            <span>{profile.name}</span>
          </button>
        )}
        <button type="button" className="link-button" onClick={signOut}>
          Sign out
        </button>
      </div>
    </header>
  );
}
