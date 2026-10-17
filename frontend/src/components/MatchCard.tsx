import { useNavigate } from 'react-router'
import type { PartidoResumen } from '../api/tipos'
import { useFavorito } from '../hooks/useFavorito'
import { etiquetaFecha } from '../lib/formato'
import { IconoEscudo, IconoEstrella } from './iconos'
import s from './MatchCard.module.css'

/** Escudo del equipo; si no hay imagen, su inicial. */
function Escudo({ url, nombre }: { url: string | null; nombre: string }) {
  return url ? (
    <img className={s.escudo} src={url} alt="" loading="lazy" width={26} height={26} />
  ) : (
    <span className={s.escudoVacio} aria-hidden="true">{nombre.charAt(0)}</span>
  )
}

/**
 * Tarjeta de partido de las listas, búsqueda y favoritos.
 * @param conLiga false cuando la lista ya está agrupada por liga
 */
export function MatchCard({ p, ligaFavorita, conLiga = true }: { p: PartidoResumen; ligaFavorita: boolean; conLiga?: boolean }) {
  const navigate = useNavigate()
  const favorito = useFavorito()
  const jugado = p.estado === 'JUGADO' && p.golesLocal !== null
  const c = p.consejo

  let tipo = s.neutra
  let titulo = 'Datos insuficientes'
  if (c) {
    if (jugado && c.acerto !== null) {
      tipo = c.acerto ? s.verde : s.rojo
      titulo = c.acerto ? 'Consejo · ✓ Acertó' : 'Consejo · ✗ Falló'
    } else {
      tipo = s.verde
      titulo = jugado ? 'Consejo · Sin dato' : c.blindado ? 'Pick blindado' : `Consejo · ${c.confianza}`
    }
  }

  return (
    <article className={s.tarjeta}>
      <div className={s.cabecera}>
        <span className={s.fecha}>
          {etiquetaFecha(p.fecha, p.estado)}
          {conLiga ? ` · ${p.liga.nombre}` : ''}
          {conLiga && ligaFavorita ? ' ★' : ''}
        </span>
        <button type="button" className={s.estrella} onClick={() => favorito.mutate(p)}
          aria-label={p.favorito ? 'Quitar de favoritos' : 'Agregar a favoritos'} aria-pressed={p.favorito}>
          <IconoEstrella llena={p.favorito} />
        </button>
      </div>
      <button type="button" className={s.abrir} onClick={() => navigate(`/partido/${p.id}`)}
        aria-label={`Ver ${p.local.nombre} contra ${p.visita.nombre}`}>
        <div className={s.equipos}>
          <div className={s.fila}>
            <span className={s.equipo}>
              <Escudo url={p.local.escudo} nombre={p.local.nombre} />
              <span className={s.nombre}>{p.local.nombre}</span>
            </span>
            {jugado ? <span className={s.goles}>{p.golesLocal}</span> : <span className={s.chipLocal}>LOCAL</span>}
          </div>
          <div className={s.fila}>
            <span className={s.equipo}>
              <Escudo url={p.visita.escudo} nombre={p.visita.nombre} />
              <span className={s.nombre}>{p.visita.nombre}</span>
            </span>
            {jugado ? <span className={s.goles}>{p.golesVisita}</span> : <span className={s.chipVisita}>VISITA</span>}
          </div>
        </div>
        <div className={`${s.franja} ${tipo}`}>
          <div className={s.franjaTextos}>
            <span className={s.franjaTitulo}>
              {c?.blindado && !jugado && <IconoEscudo size={12} />} {titulo}
            </span>
            <span className={s.franjaPick}>{c ? c.texto : 'Faltan partidos de estos equipos'}</span>
          </div>
          {c && (
            <span className={s.franjaDerecha}>
              <span className={s.franjaPct}>{c.pct}%</span>
              <span className={s.franjaCuota}>~{c.cuota.toFixed(2)}</span>
            </span>
          )}
        </div>
      </button>
    </article>
  )
}

export function MatchCardSkeleton() {
  return (
    <div className={s.esqueleto} aria-hidden="true">
      <div className={s.hueso} style={{ width: '45%', height: 12 }} />
      <div className={s.hueso} style={{ width: '70%', height: 20 }} />
      <div className={s.hueso} style={{ width: '60%', height: 20 }} />
      <div className={s.hueso} style={{ width: '100%', height: 52, borderRadius: 12 }} />
    </div>
  )
}
