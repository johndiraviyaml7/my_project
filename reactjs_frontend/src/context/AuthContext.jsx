import React, { createContext, useContext, useState } from 'react';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [isAuthenticated, setIsAuthenticated] = useState(
    () => localStorage.getItem('qm_auth') === 'true'
  );
  const [user, setUser] = useState(
    () => JSON.parse(localStorage.getItem('qm_user') || 'null') || { name: 'Dr. John Conor', email: 'admin@quantixmed.com' }
  );

  const login = (email, password) => {
    if (email === 'admin@quantixmed.com' && password === 'Admin@123') {
      const u = { name: 'Dr. John Conor', email };
      setUser(u);
      setIsAuthenticated(true);
      localStorage.setItem('qm_auth', 'true');
      localStorage.setItem('qm_user', JSON.stringify(u));
      return true;
    }
    return false;
  };

  const logout = () => {
    setIsAuthenticated(false);
    setUser(null);
    localStorage.removeItem('qm_auth');
    localStorage.removeItem('qm_user');
  };

  return (
    <AuthContext.Provider value={{ isAuthenticated, user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
