import s from './Segments.module.css'

/** Pestañas Jugados · Hoy · Próximos (la activa con el color del acento). */
export function Segments<T extends string>({ opciones, activa, onCambio }: {
  opciones: { valor: T; texto: string }[]
  activa: T
  onCambio: (v: T) => void
}) {
  return (
    <div className={s.grupo}>
      <div className={s.caja} role="tablist" style={{ gridTemplateColumns: `repeat(${opciones.length}, minmax(0, 1fr))` }}>
        {opciones.map((o) => (
          <button key={o.valor} type="button" role="tab" aria-selected={o.valor === activa}
            className={`${s.boton} ${o.valor === activa ? s.activo : ''}`} onClick={() => onCambio(o.valor)}>
            {o.texto}
          </button>
        ))}
      </div>
    </div>
  )
}
