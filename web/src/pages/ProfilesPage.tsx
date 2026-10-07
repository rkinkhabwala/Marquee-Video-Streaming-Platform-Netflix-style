import { useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { createProfile } from '../api/endpoints';
import { queryKeys, useProfiles } from '../api/queries';
import type { Profile } from '../api/types';
import { setProfile } from '../auth/session';
import { gradientFor } from '../components/Artwork';

const MAX_PROFILES = 5;

export function ProfilesPage() {
  const { data: profiles, isPending, error } = useProfiles();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [adding, setAdding] = useState(false);

  const choose = (profile: Profile) => {
    setProfile({ id: profile.id, name: profile.name, isKids: profile.isKids });
    queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'profiles' });
    navigate('/', { replace: true });
  };

  return (
    <main className="profiles-page">
      <h1>Who&apos;s watching?</h1>
      {isPending && <p className="muted">Loading profiles…</p>}
      {error && <p className="form-error">Could not load profiles.</p>}
      <ul className="profiles-grid">
        {profiles?.map((profile) => (
          <li key={profile.id}>
            <button type="button" className="profile-tile" onClick={() => choose(profile)} aria-label={profile.name}>
              <span className="avatar" style={{ background: gradientFor(profile.name) }} data-kids={profile.isKids || undefined}>
                {profile.name.charAt(0).toUpperCase()}
              </span>
              <span>{profile.name}</span>
              {profile.isKids && <span className="badge">Kids</span>}
            </button>
          </li>
        ))}
        {profiles && profiles.length < MAX_PROFILES && !adding && (
          <li>
            <button type="button" className="profile-tile" onClick={() => setAdding(true)}>
              <span className="avatar avatar--add">+</span>
              <span>Add profile</span>
            </button>
          </li>
        )}
      </ul>
      {adding && (
        <AddProfileForm
          onDone={() => {
            setAdding(false);
            void queryClient.invalidateQueries({ queryKey: queryKeys.profiles });
          }}
        />
      )}
    </main>
  );
}

function AddProfileForm({ onDone }: { onDone: () => void }) {
  const [name, setName] = useState('');
  const [isKids, setIsKids] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    try {
      await createProfile(name.trim(), isKids);
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not create the profile.');
    }
  };

  return (
    <form className="inline-form" onSubmit={submit}>
      <input aria-label="Profile name" placeholder="Name" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
      <label className="checkbox">
        <input type="checkbox" checked={isKids} onChange={(e) => setIsKids(e.target.checked)} /> Kids profile
      </label>
      <button type="submit" className="button button--primary">
        Add
      </button>
      <button type="button" className="button button--secondary" onClick={onDone}>
        Cancel
      </button>
      {error && <p className="form-error">{error}</p>}
    </form>
  );
}
