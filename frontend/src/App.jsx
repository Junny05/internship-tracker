import { useCallback, useState } from "react";
import LoginPage from "./pages/LoginPage";
import Dashboard from "./pages/Dashboard";

const TOKEN_KEY = "internship-tracker-token";

function decodeEmail(token) {
  try {
    const payload = token.split(".")[1];
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(json).sub ?? null;
  } catch {
    return null;
  }
}

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));

  const handleAuthenticated = useCallback((newToken) => {
    localStorage.setItem(TOKEN_KEY, newToken);
    setToken(newToken);
  }, []);

  const handleLogout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setToken(null);
  }, []);

  if (!token) {
    return <LoginPage onAuthenticated={handleAuthenticated} />;
  }

  return <Dashboard token={token} email={decodeEmail(token)} onLogout={handleLogout} />;
}
