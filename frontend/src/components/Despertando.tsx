import { LogoIcono } from './Logo'
import s from './Despertando.module.css'

/** Se muestra mientras el backend (Render gratis) despierta, o mientras carga la sesión. */
export function Despertando({ texto, detalle }: { texto: string; detalle?: string }) {
  return (
    <div className={s.pantalla} role="status" aria-live="polite">
      <LogoIcono tamano="grande" />
      <div className={s.puntos} aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <p className={s.texto}>{texto}</p>
      {detalle && <p className={s.detalle}>{detalle}</p>}
    </div>
  )
}
