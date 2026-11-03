import { Navigate } from 'react-router-dom';
import Login from '@/components/Login';
import { useAuth } from '@/providers/AuthProvider';
import { ROUTE_PATHS } from '@/router/routePaths';

export default function LoginPage() {
  const { isAuthenticated, login } = useAuth();

  if (isAuthenticated) {
    return <Navigate to={ROUTE_PATHS.zhibi} replace />;
  }

  return <Login onLogin={login} />;
}
