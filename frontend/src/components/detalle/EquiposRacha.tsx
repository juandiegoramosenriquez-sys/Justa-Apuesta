import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { partidosApi } from '../../api/partidos'
import type { Analisis } from '../../api/tipos'
import { diaMesCorto } from '../../lib/formato'
import ui from '../../styles/ui.module.css'
import s from './Detalle.module.css'

const ESTADO: Record<string, string> = { JUGADO: 'Final', HOY: 'Hoy', PROXIMO: 'Próximo', EN_VIVO: 'En vivo', SUSPENDIDO: 'Suspendido' }

function Racha({ racha }: { racha: string }) {
  return (
    <>
      {racha.split('').map((r, i) => (
        <span key={i} className={`${s.resultado} ${s[r as 'G' | 'E' | 'P']}`}>{r}</span>
      ))}
    </>
  )
}

/** Últimos 5 de un equipo (se piden al tocar su racha). */
function TeamHistory({ equipoId }: { equipoId: number }) {
  const q = useQuery({ queryKey: ['ultimos', equipoId], queryFn: () => partidosApi.ultimos(equipoId) })
  if (q.isPending) return <div className={s.historial}><span className={ui.sub}>Cargando…</span></div>
  if (q.isError) return <div className={s.historial}><span className={ui.sub}>No pudimos cargar sus partidos.</span></div>
  return (
    <div className={s.historial}>
      <span className={s.historialTitulo}>Últimos {q.data.partidos.length} de {q.data.equipo} · {q.data.resumen}</span>
      {q.data.partidos.map((m) => (
        <div key={m.id} className={s.historialFila}>
          <span className={`${s.resultado} ${s[m.resultado]}`}>{m.resultado}</span>
          <div className={s.historialTextos}>
            <span style={{ fontSize: 13 }}>{m.enCasa ? 'en casa vs' : 'vs'} {m.rival}</span>
            <span className={ui.sub} style={{ fontSize: 11 }}>{diaMesCorto(m.fecha)}</span>
          </div>
          <span className={s.marcador}>{m.golesFavor}-{m.golesContra}</span>
        </div>
      ))}
    </div>
  )
}

/** Equipos con "VS" o el marcador final, el estadio y las rachas tocables. */
export function EquiposRacha({ a }: { a: Analisis }) {
  const [abierta, setAbierta] = useState<'h' | 'a' | null>(null)
  const p = a.partido
  const jugado = p.estado === 'JUGADO' && p.golesLocal !== null
  const conRacha = a.rachaLocal.length > 0 || a.rachaVisita.length > 0

  return (
    <section className={ui.tarjeta}>
      <div className={s.equipos}>
        <div className={s.lado}>
          <span className={s.etiquetaLocal}>LOCAL</span>
          <span className={s.nombreEquipo}>{p.local.nombre}</span>
        </div>
        <div className={s.centro}>
          <span className={s.centroTexto} style={{ fontSize: jugado ? 36 : 24, color: jugado ? 'var(--texto)' : 'var(--acento-texto)' }}>
            {jugado ? `${p.golesLocal} - ${p.golesVisita}` : 'VS'}
          </span>
          <span className={s.estadoTexto}>{ESTADO[p.estado]}</span>
        </div>
        <div className={s.ladoDerecho}>
          <span className={s.etiquetaVisita}>VISITANTE</span>
          <span className={s.nombreEquipo}>{p.visita.nombre}</span>
        </div>
      </div>
      <span className={ui.sub}>{p.estadio ?? 'Estadio por confirmar'}</span>
      {conRacha && (
        <div className={s.rachas}>
          <button type="button" className={`${s.racha} ${abierta === 'h' ? s.rachaActiva : ''}`} style={{ justifyContent: 'flex-start' }}
            onClick={() => setAbierta(abierta === 'h' ? null : 'h')} aria-expanded={abierta === 'h'}
            aria-label={`Ver últimos partidos de ${p.local.nombre}`}>
            <Racha racha={a.rachaLocal} />
          </button>
          <button type="button" className={`${s.racha} ${abierta === 'a' ? s.rachaActiva : ''}`} style={{ justifyContent: 'flex-end' }}
            onClick={() => setAbierta(abierta === 'a' ? null : 'a')} aria-expanded={abierta === 'a'}
            aria-label={`Ver últimos partidos de ${p.visita.nombre}`}>
            <Racha racha={a.rachaVisita} />
          </button>
        </div>
      )}
      {abierta && <TeamHistory equipoId={abierta === 'h' ? p.local.id : p.visita.id} />}
    </section>
  )
}
