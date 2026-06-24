import React, { createContext, useState, useContext, useEffect } from 'react';
import api from '../services/api';
import { clearAuthSession, getAuthToken, getAuthUserRaw, setAuthSession } from '../services/authSession';

export const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = getAuthToken();
    const savedUser = getAuthUserRaw();
    if (token && savedUser) {
      setUser(JSON.parse(savedUser));
      api.defaults.headers.common['Authorization'] = `Bearer ${token}`;
    }
    setLoading(false);
  }, []);

  const login = async (enterpriseId, password) => {
    const res = await api.post('/api/auth/login', { enterpriseId, password });
    const { token, ...userData } = res.data;
    setAuthSession({ token, user: userData });
    api.defaults.headers.common['Authorization'] = `Bearer ${token}`;
    setUser(userData);
    return userData;
  };

  const setPreferredLanguage = async (preferredLanguage) => {
    if (!user) return;
    const res = await api.put('/api/auth/language', { preferredLanguage });
    const updatedUser = { ...user, ...res.data };
    setUser(updatedUser);
    setAuthSession({ token: getAuthToken(), user: updatedUser });
  };

  const logout = () => {
    clearAuthSession();
    delete api.defaults.headers.common['Authorization'];
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, setPreferredLanguage }}>
      {!loading && children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
