import ui from '../styles/ui.module.css'

/** Error amable con botón para reintentar. */
export function ErrorCaja({ mensaje, onReintentar }: { mensaje: string; onReintentar?: () => void }) {
  return (
    <div className={ui.error} role="alert">
      <span>{mensaje}</span>
      {onReintentar && (
        <button type="button" className={ui.botonReintentar} onClick={onReintentar}>
          Reintentar
        </button>
      )}
    </div>
  )
}

/** Aviso suave (por ejemplo, datos de la caché). */
export function Aviso({ texto }: { texto: string }) {
  return (
    <div className={ui.aviso} role="status">
      {texto}
    </div>
  )
}

/** Indicador de "jalar para actualizar". */
export function IndicadorJalar({ jalado, refrescando, listo }: { jalado: number; refrescando: boolean; listo: boolean }) {
  if (!jalado && !refrescando) return null
  return (
    <div style={{ height: refrescando ? 36 : jalado / 2, display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontSize: 12, color: 'var(--texto-secundario)', overflow: 'hidden' }} role="status">
      {refrescando ? 'Actualizando…' : listo ? 'Suelta para actualizar' : 'Jala para actualizar'}
    </div>
  )
}
