import { Outlet } from 'react-router'
import { useBoleto } from '../hooks/useBoleto'
import { BoletoSheet } from './BoletoSheet'
import { BottomNav } from './BottomNav'
import s from './AppLayout.module.css'

/** Pantallas con sesión: menú (abajo en celular, a la izquierda en PC), contenido y el boleto. */
export function AppLayout() {
  const { picks } = useBoleto()
  return (
    <div className={s.app}>
      <BottomNav />
      <div className={`${s.contenido} ${picks.length > 0 ? s.conBoleto : ''}`}>
        <Outlet />
      </div>
      <BoletoSheet />
    </div>
  )
}
