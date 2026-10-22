import { NavLink } from 'react-router'
import { IconoFavoritos, IconoPartidos, IconoPerfil, IconoPicks } from './iconos'
import { LogoIcono, LogoTexto } from './Logo'
import s from './BottomNav.module.css'

const ITEMS = [
  { a: '/', texto: 'Partidos', Icono: IconoPartidos },
  { a: '/picks', texto: 'Picks', Icono: IconoPicks },
  { a: '/favoritos', texto: 'Favoritos', Icono: IconoFavoritos },
  { a: '/perfil', texto: 'Perfil', Icono: IconoPerfil },
]

/** Menú principal: barra inferior en celular, columna izquierda en PC. La activa en verde. */
export function BottomNav() {
  return (
    <nav className={s.barra} aria-label="Navegación principal">
      <div className={s.marca}>
        <LogoIcono tamano="cabecera" />
        <LogoTexto tamano="cabecera" />
      </div>
      {ITEMS.map(({ a, texto, Icono }) => (
        <NavLink key={a} to={a} end={a === '/'} className={({ isActive }) => `${s.item} ${isActive ? s.activo : ''}`}>
          {({ isActive }) => (
            <>
              <Icono color={isActive ? 'var(--acento-texto-claro)' : 'var(--texto-apagado)'} />
              {texto}
            </>
          )}
        </NavLink>
      ))}
    </nav>
  )
}
