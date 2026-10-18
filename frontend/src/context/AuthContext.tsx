import React, { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';
import { User, LoginCredentials, loginApi, checkSessionApi, logoutApi } from '../services/auth';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  sessionExpired: boolean;
  login: (credentials: LoginCredentials) => Promise<User>;
  logout: () => Promise<void>;
  checkSession: (force?: boolean) => Promise<User | null>;
  clearSessionExpired: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [sessionExpired, setSessionExpired] = useState<boolean>(false);

  // Keep a stable ref to avoid re-triggering callbacks or dependency changes
  const userRef = useRef<User | null>(user);
  userRef.current = user;

  const isCheckingRef = useRef<boolean>(false);
  const lastCheckTimestampRef = useRef<number>(0);

  const checkSession = useCallback(async (force = false): Promise<User | null> => {
    const now = Date.now();
    // Prevent duplicate simultaneous in-flight checks
    if (isCheckingRef.current) {
      return userRef.current;
    }
    // Throttle checks to max once every 3 seconds unless forced
    if (!force && now - lastCheckTimestampRef.current < 3000) {
      return userRef.current;
    }

    isCheckingRef.current = true;
    lastCheckTimestampRef.current = now;

    try {
      const currentUser = await checkSessionApi();
      if (currentUser) {
        setUser((prev) => {
          if (prev?.id === currentUser.id && prev?.username === currentUser.username) {
            return prev;
          }
          return currentUser;
        });
        setSessionExpired(false);
        return currentUser;
      } else {
        if (userRef.current) {
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
      isCheckingRef.current = false;
      setIsLoading(false);
    }
  }, []); // Empty dependencies = stable reference across renders, 0 infinite loops

  useEffect(() => {
    // Initial session verification on application mount only
    checkSession(true);
  }, [checkSession]);

  const login = async (credentials: LoginCredentials): Promise<User> => {
    setIsLoading(true);
    try {
      const authenticatedUser = await loginApi(credentials);
      setUser(authenticatedUser);
      setSessionExpired(false);
      lastCheckTimestampRef.current = Date.now();
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
      lastCheckTimestampRef.current = 0;
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
