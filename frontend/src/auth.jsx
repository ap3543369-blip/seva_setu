import { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { api, post } from './api.js';

const Ctx = createContext(null);
export const useAuth = () => useContext(Ctx);
/** Home of the roles that already have a dashboard; every other role starts at the SevaSetu hub. */
export const dashboardPath = role => (role === 'ngo' ? '/ngo' : role === 'admin' ? '/admin' : role === 'donor' ? '/donor' : role === 'recipient' ? '/books' : role === 'hospital' ? '/medishare' : role === 'recycler' ? '/ecoai' : '/hub');
export const VERIFY_ROLES = ['ngo', 'hospital', 'recycler'];

export function AuthProvider({ children }) {
  const [user, setUser] = useState(undefined); // undefined = still checking the session

  useEffect(() => {
    api('/auth/me').then(setUser).catch(() => setUser(null));
  }, []);

  const login = useCallback(async (email, password) => {
    const u = await post('/auth/login', { email, password });
    setUser(u);
    return u;
  }, []);
  const signup = useCallback(async body => {
    const u = await post('/auth/signup', body);
    setUser(u);
    return u;
  }, []);
  const logout = useCallback(async () => {
    try { await post('/auth/logout'); } catch (e) { /* already logged out */ }
    setUser(null);
  }, []);

  return <Ctx.Provider value={{ user, setUser, login, signup, logout }}>{children}</Ctx.Provider>;
}
