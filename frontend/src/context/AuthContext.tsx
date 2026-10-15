import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { User, LoginCredentials, loginApi, checkSessionApi, logoutApi } from '../services/auth';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  sessionExpired: boolean;
  login: (credentials: LoginCredentials) => Promise<User>;
  logout: () => Promise<void>;
  checkSession: () => Promise<User | null>;
  clearSessionExpired: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [sessionExpired, setSessionExpired] = useState<boolean>(false);

  const checkSession = useCallback(async (): Promise<User | null> => {
    try {
      const currentUser = await checkSessionApi();
      if (currentUser) {
        setUser(currentUser);
        setSessionExpired(false);
        return currentUser;
      } else {
        if (user) {
          // User was previously logged in, now session is invalid
          setSessionExpired(true);
        }
        setUser(null);
        return null;
      }
    } catch {
      setUser(null);
      return null;
    } finally {
      setIsLoading(false);
    }
  }, [user]);

  useEffect(() => {
    // Initial session verification on application mount
    checkSession();
  }, [checkSession]);

  const login = async (credentials: LoginCredentials): Promise<User> => {
    setIsLoading(true);
    try {
      const authenticatedUser = await loginApi(credentials);
      setUser(authenticatedUser);
      setSessionExpired(false);
      return authenticatedUser;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async (): Promise<void> => {
    setIsLoading(true);
    try {
      await logoutApi();
    } finally {
      setUser(null);
      setSessionExpired(false);
      setIsLoading(false);
    }
  };

  const clearSessionExpired = () => {
    setSessionExpired(false);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        sessionExpired,
        login,
        logout,
        checkSession,
        clearSessionExpired,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
