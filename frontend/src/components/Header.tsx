import type { Usuario } from '../api/tipos'
import { MODOS } from '../lib/modos'
import { LogoIcono, LogoTexto } from './Logo'
import s from './Header.module.css'

export function Header({ usuario, onModo }: { usuario: Usuario; onModo: () => void }) {
  const primerNombre = usuario.nombre.trim().split(' ')[0]
  return (
    <header className={s.cabecera}>
      <div className={s.marca}>
        <LogoIcono tamano="cabecera" />
        <div className={s.textos}>
          <LogoTexto tamano="cabecera" />
          <div className={s.hola}>Hola, {primerNombre}</div>
        </div>
      </div>
      <button type="button" className={s.modo} onClick={onModo}>
        Modo {MODOS[usuario.modo].nombre}
      </button>
    </header>
  )
}
