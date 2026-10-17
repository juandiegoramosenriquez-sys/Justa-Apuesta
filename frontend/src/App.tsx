import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { useSesion } from './auth/sesion'
import { AppLayout } from './components/AppLayout'
import { Despertando } from './components/Despertando'
import { useServidorListo } from './hooks/useServidorListo'
import { BoletoPublicoPage } from './pages/BoletoPublicoPage'
import { FavoritesPage } from './pages/FavoritesPage'
import { LoginPage } from './pages/LoginPage'
import { MatchDetailPage } from './pages/MatchDetailPage'
import { MatchesPage } from './pages/MatchesPage'
import { PicksPage } from './pages/PicksPage'
import { ProfilePage } from './pages/ProfilePage'
import { RegisterPage } from './pages/RegisterPage'

/** Solo con sesión; si no, al login. */
function ConSesion({ children }: { children: ReactNode }) {
  const { token, cargandoUsuario } = useSesion()
  if (!token) return <Navigate to="/login" replace />
  if (cargandoUsuario) return <Despertando texto="Cargando tu cuenta…" />
  return children
}

/** Login y registro: si ya hay sesión, a la app. */
function SinSesion({ children }: { children: ReactNode }) {
  const { token } = useSesion()
  return token ? <Navigate to="/" replace /> : children
}

export default function App() {
  const { listo, lento } = useServidorListo()

  if (!listo) {
    return lento ? <Despertando texto="Despertando el servidor… puede tardar hasta un minuto." /> : null
  }

  return (
    <Routes>
      <Route path="/login" element={<SinSesion><LoginPage /></SinSesion>} />
      <Route path="/registro" element={<SinSesion><RegisterPage /></SinSesion>} />
      {/* Boleto compartido: se abre con o sin sesión */}
      <Route path="/b/:codigo" element={<BoletoPublicoPage />} />
      <Route element={<ConSesion><AppLayout /></ConSesion>}>
        <Route path="/" element={<MatchesPage />} />
        <Route path="/partido/:id" element={<MatchDetailPage />} />
        <Route path="/picks" element={<PicksPage />} />
        <Route path="/favoritos" element={<FavoritesPage />} />
        <Route path="/perfil" element={<ProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
