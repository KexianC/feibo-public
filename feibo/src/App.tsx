import './styles/fonts.css';
import { HashRouter } from 'react-router-dom';
import { AuthProvider } from '@/providers/AuthProvider';
import AppRouter from '@/router/AppRouter';

export default function App() {
  return (
    <HashRouter>
      <AuthProvider>
        <AppRouter />
      </AuthProvider>
    </HashRouter>
  );
}
