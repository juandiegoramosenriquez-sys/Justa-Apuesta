import { useState } from 'react'
import type { Analisis } from '../../api/tipos'
import { useBoleto } from '../../hooks/useBoleto'
import { COLOR, colorTono } from '../../lib/formato'
import { IconoCheck, IconoEscudo, IconoMas } from '../iconos'
import { Pruebas } from './Prediccion'
import ui from '../../styles/ui.module.css'
import s from './Detalle.module.css'

const colorPct = (v: number) => (v >= 65 ? COLOR.verde : v <= 35 ? COLOR.rojo : 'var(--texto)')

/** Enfrentamientos anteriores reales: resumen y los 6 últimos duelos. */
export function HeadToHead({ a }: { a: Analisis }) {
  const h = a.h2h
  return (
    <section className={ui.seccion}>
      <h2 className={ui.tituloSeccion}>Enfrentamientos anteriores</h2>
      {!h ? (
        <div className={ui.tarjeta18}>
          <span className={ui.sub}>No encontramos enfrentamientos anteriores entre estos equipos.</span>
        </div>
      ) : (
        <div className={ui.tarjeta18}>
          <div className={s.tresColumnas}>
            <div className={s.caja} style={{ background: 'var(--acento-fondo)' }}>
              <span className={s.pct26} style={{ color: 'var(--acento-texto)' }}>{h.hw}</span>
              <span className={ui.sub} style={{ fontSize: 11 }}>Gana {a.partido.local.nombre}</span>
            </div>
            <div className={s.caja} style={{ background: 'var(--neutro-3)', alignItems: 'center', textAlign: 'center' }}>
              <span className={s.pct26}>{h.d}</span>
              <span className={ui.sub} style={{ fontSize: 11 }}>Empates</span>
            </div>
            <div className={s.caja} style={{ background: 'var(--acento-fondo)', alignItems: 'flex-end', textAlign: 'right' }}>
              <span className={s.pct26} style={{ color: 'var(--acento-texto-claro)' }}>{h.aw}</span>
              <span className={ui.sub} style={{ fontSize: 11 }}>Gana {a.partido.visita.nombre}</span>
            </div>
          </div>
          <div className={s.tresColumnas} style={{ textAlign: 'center' }}>
            <div className={s.columnaCentro}>
              <span className={s.miniPct}>{h.goles.toFixed(1)}</span>
              <span className={ui.sub} style={{ fontSize: 11 }}>Goles por partido</span>
            </div>
            <div className={s.columnaCentro}>
              <span className={s.miniPct} style={{ color: colorPct(h.btts) }}>{h.btts}%</span>
              <span className={ui.sub} style={{ fontSize: 11 }}>Ambos marcan</span>
            </div>
            <div className={s.columnaCentro}>
              <span className={s.miniPct} style={{ color: colorPct(h.over25) }}>{h.over25}%</span>
              <span className={ui.sub} style={{ fontSize: 11 }}>Más de 2.5</span>
            </div>
          </div>
          {h.lista.map((x, i) => {
            const gl = x.golesLocal > x.golesVisita, gv = x.golesVisita > x.golesLocal
            return (
              <div key={i} className={s.duelo}>
                <span className={ui.sub} style={{ fontSize: 11 }}>{x.fecha}{x.estadio ? ` · ${x.estadio}` : ''}</span>
                <div className={s.dueloFila}>
                  <span className={s.dueloEquipo} style={{ fontWeight: gl ? 700 : 400, color: gl ? 'var(--texto)' : 'var(--texto-secundario)' }}>{x.local}</span>
                  <span className={s.dueloMarcador}>{x.golesLocal} - {x.golesVisita}</span>
                  <span className={s.dueloEquipo} style={{ textAlign: 'right', fontWeight: gv ? 700 : 400, color: gv ? 'var(--texto)' : 'var(--texto-secundario)' }}>{x.visita}</span>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </section>
  )
}

/** Categorías que no salen por falta de estadísticas reales ("Corners", "Tarjetas", "Remates"). */
function faltantes(a: Analisis): string[] {
  const hay = new Set(a.categorias.map((c) => c.clave))
  return [
    ['corners', 'Corners'],
    ['tarjetas', 'Tarjetas'],
    ['remates', 'Remates'],
  ].filter(([clave]) => !hay.has(clave)).map(([, nombre]) => nombre)
}

/** Todos los mercados: pestañas por categoría, filas con certeza, cuota, pruebas (al tocar) y botón para el boleto. */
export function MarketTabs({ a }: { a: Analisis }) {
  const [tab, setTab] = useState(a.categorias[0]?.clave ?? 'goles')
  const [abierto, setAbierto] = useState<string | null>(null)
  const boleto = useBoleto()
  const cat = a.categorias.find((c) => c.clave === tab) ?? a.categorias[0]
  const jugado = a.resultadoFinal !== null
  const puedeAgregar = a.partido.estado === 'HOY' || a.partido.estado === 'PROXIMO'
  if (!cat) return null

  return (
    <section className={ui.seccion}>
      <h2 className={ui.tituloSeccion}>Todos los mercados</h2>
      <div className={s.pestanas} role="tablist">
        {a.categorias.map((c) => (
          <button key={c.clave} type="button" role="tab" aria-selected={c.clave === cat.clave}
            className={`${s.pestana} ${c.clave === cat.clave ? s.pestanaActiva : ''}`} onClick={() => setTab(c.clave)}>
            {c.nombre}
          </button>
        ))}
      </div>
      <div className={s.leyenda}>
        <span><span className={s.punto} style={{ background: COLOR.verde }} />65%+</span>
        <span><span className={s.punto} style={{ background: COLOR.rojo }} />35% o menos</span>
        <span className={s.escudo}><IconoEscudo size={12} /></span><span>Blindado</span>
        {jugado && <span>✓ salió · ✗ no salió</span>}
      </div>
      {faltantes(a).length > 0 && <span className={ui.sub}>Sin estadísticas suficientes: {faltantes(a).join(', ')}.</span>}
      <div className={s.listaMercados} role="tabpanel">
        {cat.mercados.map((m) => {
          const color = colorTono(m.tono)
          return (
            <div key={m.clave} className={s.mercado}>
              <div className={s.mercadoFila}>
                <button type="button" className={s.mercadoBoton} aria-expanded={abierto === m.clave}
                  onClick={() => setAbierto(abierto === m.clave ? null : m.clave)}
                  style={{ color: m.tono === 'VERDE' ? 'var(--texto)' : 'var(--texto-suave)' }}>
                  {m.blindado && <span className={s.escudo} aria-label="Pick blindado"><IconoEscudo size={13} /></span>}
                  <span>{m.texto}</span>
                  {m.salio !== null && (
                    <span style={{ fontWeight: 700, color: m.salio ? COLOR.verde : COLOR.rojo }}>{m.salio ? '✓' : '✗'}</span>
                  )}
                </button>
                <span className={s.mercadoDerecha}>
                  <span className={s.miniPct} style={{ color }}>{m.pct}%</span>
                  <span className={s.chipCuota}>~{m.cuota.toFixed(2)}</span>
                  {puedeAgregar && (
                    <button type="button" className={`${s.agregar} ${boleto.tiene(a.partido.id, m.clave) ? s.agregado : ''}`}
                      aria-label={boleto.tiene(a.partido.id, m.clave) ? `Quitar ${m.texto} del boleto` : `Agregar ${m.texto} al boleto`}
                      aria-pressed={boleto.tiene(a.partido.id, m.clave)} disabled={boleto.alternar.isPending}
                      onClick={() => boleto.alternar.mutate({ partidoId: a.partido.id, clave: m.clave })}>
                      {boleto.tiene(a.partido.id, m.clave) ? <IconoCheck size={14} /> : <IconoMas size={14} />}
                    </button>
                  )}
                </span>
              </div>
              <div className={s.barraFondo}>
                <div className={s.barraRelleno} style={{ width: `${m.pct}%`, background: color }} />
              </div>
              {abierto === m.clave && m.pruebas.length > 1 && <Pruebas pruebas={m.pruebas} />}
            </div>
          )
        })}
        <div className={s.promedio}>
          {cat.promedio}
          {a.resultadoFinal ? ` · ${a.resultadoFinal.resumen}` : ''}
        </div>
      </div>
    </section>
  )
}

/** Cara a cara (promedio): barras comparativas con datos reales de cada equipo. */
export function CompareBars({ a }: { a: Analisis }) {
  return (
    <section className={ui.seccion}>
      <h2 className={ui.tituloSeccion}>Cara a cara (promedio)</h2>
      <div className={ui.tarjeta18} style={{ gap: 14 }}>
        <div className={s.comparaFila} style={{ fontSize: 12, fontWeight: 700 }}>
          <span style={{ color: 'var(--acento-texto)' }}>{a.partido.local.nombre}</span>
          <span style={{ color: 'var(--texto-secundario)' }}>{a.partido.visita.nombre}</span>
        </div>
        {a.caraACara.map((c) => {
          const total = c.local + c.visita
          return (
            <div key={c.etiqueta} className={s.compara}>
              <div className={s.comparaFila}>
                <span style={{ fontWeight: 700 }}>{c.local.toFixed(1)}</span>
                <span style={{ color: 'var(--texto-secundario)' }}>{c.etiqueta}</span>
                <span style={{ fontWeight: 700 }}>{c.visita.toFixed(1)}</span>
              </div>
              <div className={s.barraCompara}>
                <div style={{ height: 8, width: `${total > 0 ? Math.round((c.local / total) * 100) : 50}%`, background: 'var(--acento)' }} />
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}

/** Jugadores clave con sus promedios reales (se oculta si el proveedor no trae jugadores). */
export function KeyPlayers({ a }: { a: Analisis }) {
  if (a.jugadores.length === 0) return null
  return (
    <section className={ui.seccion}>
      <h2 className={ui.tituloSeccion}>Jugadores clave</h2>
      {a.jugadores.map((p, i) => (
        <div key={i} className={s.jugador}>
          <div className={s.dorsal}>{p.num}</div>
          <div className={s.jugadorTextos}>
            <span style={{ fontSize: 14, fontWeight: 500 }}>{p.nombre}</span>
            <span className={ui.sub}>{p.equipo} · {p.stat}</span>
          </div>
          <div className={s.jugadorPct}>
            <span className={s.miniPct} style={{ color: colorTono(p.tono) }}>{p.pct}%</span>
            <span className={ui.sub} style={{ fontSize: 11 }}>{p.que}</span>
          </div>
        </div>
      ))}
    </section>
  )
}
