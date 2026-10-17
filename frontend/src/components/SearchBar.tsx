import { IconoBuscar } from './iconos'
import s from './SearchBar.module.css'

export function SearchBar({ valor, onCambio, buscando }: { valor: string; onCambio: (v: string) => void; buscando: boolean }) {
  return (
    <div className={s.contenedor}>
      <label className={s.caja}>
        <IconoBuscar />
        <span className="solo-lectores">Buscar equipo</span>
        <input className={s.input} type="search" value={valor} onChange={(e) => onCambio(e.target.value)}
          placeholder="Busca cualquier equipo o liga" autoComplete="off" enterKeyHint="search" />
        {buscando && <span className={s.spinner} role="status" aria-label="Buscando" />}
      </label>
    </div>
  )
}
