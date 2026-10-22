import type { Usuario } from '../api/tipos'
import { MODOS } from '../lib/modos'
import s from './Header.module.css'

/** "sábado 25 oct" */
function hoy() {
  return new Date()
    .toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'short' })
    .replace(',', '')
    .replace('.', '')
}

export function Header({ usuario, onModo }: { usuario: Usuario; onModo: () => void }) {
  const primerNombre = usuario.nombre.trim().split(' ')[0]
  return (
    <header className={s.cabecera}>
      <div className={s.textos}>
        <div className={s.titulo}>Justa Apuesta</div>
        <div className={s.hola}>
          <span className={s.nombre}>Hola, {primerNombre}</span>
          <span className={s.fecha}> · {hoy()}</span>
        </div>
      </div>
      <button type="button" className={s.modo} onClick={onModo} aria-label={`Modo ${MODOS[usuario.modo].nombre}`}>
        {MODOS[usuario.modo].nombre}
      </button>
    </header>
  )
}
