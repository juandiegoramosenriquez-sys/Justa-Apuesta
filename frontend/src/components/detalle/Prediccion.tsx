import type { Analisis, PickBreve, Prueba } from '../../api/tipos'
import { IconoCerrar, IconoCheck, IconoEscudo } from '../iconos'
import { COLOR } from '../../lib/formato'
import { MODOS } from '../../lib/modos'
import type { Modo } from '../../api/tipos'
import ui from '../../styles/ui.module.css'
import s from './Detalle.module.css'

/** ¿Quién gana? Barra de 3 colores, porcentajes (el mayor en verde) y veredicto. */
export function WinProbability({ a }: { a: Analisis }) {
  const g = a.ganador!
  const top = Math.max(g.h, g.d, g.a)
  const color = (v: number) => (v === top ? COLOR.verde : 'var(--texto)')
  return (
    <section className={ui.tarjeta}>
      <div className={ui.filaTitulo}>
        <h2 className={ui.tituloGrande}>¿Quién gana?</h2>
        <span className={ui.sub} style={{ fontSize: 11 }}>{a.h2h ? 'Modelo + directos' : 'Modelo'}</span>
      </div>
      <div className={s.barraGanador} role="img" aria-label={`Local ${g.h}%, empate ${g.d}%, visitante ${g.a}%`}>
        <div style={{ width: `${g.h}%`, background: 'var(--acento)' }} />
        <div style={{ width: `${g.d}%`, background: 'var(--neutro-2)' }} />
        <div style={{ width: `${g.a}%`, background: 'var(--texto)' }} />
      </div>
      <div className={s.tresColumnas}>
        <div className={s.columna}>
          <span className={s.pct26} style={{ color: color(g.h) }}>{g.h}%</span>
          <span className={ui.sub}>Gana {a.partido.local.nombre}</span>
        </div>
        <div className={s.columnaCentro}>
          <span className={s.pct26} style={{ color: color(g.d) }}>{g.d}%</span>
          <span className={ui.sub}>Empate</span>
        </div>
        <div className={s.columnaDerecha}>
          <span className={s.pct26} style={{ color: color(g.a) }}>{g.a}%</span>
          <span className={ui.sub}>Gana {a.partido.visita.nombre}</span>
        </div>
      </div>
      <div className={s.veredicto}>{g.veredicto}</div>
    </section>
  )
}

/** Las 5 pruebas con datos reales: ✓ pasó, ✗ no pasó, – sin datos (no cuenta). */
export function Pruebas({ pruebas }: { pruebas: Prueba[] }) {
  return (
    <ul className={s.pruebas}>
      {pruebas.map((p) => (
        <li key={p.nombre} className={s.prueba}>
          <span className={`${s.pruebaIcono} ${p.paso === true ? s.pruebaSi : p.paso === false ? s.pruebaNo : s.pruebaNa}`}>
            {p.paso === true ? <IconoCheck size={12} /> : p.paso === false ? <IconoCerrar size={12} /> : '–'}
          </span>
          <span className={s.pruebaNombre}>{p.nombre}</span>
          <span className={s.pruebaDetalle}>{p.detalle}</span>
        </li>
      ))}
    </ul>
  )
}

/** El consejo del partido: certeza, cuota, pick blindado con sus pruebas y agregar al boleto. */
export function AdviceCard({ a, modo, enBoleto, onBoleto, ocupado }: {
  a: Analisis
  modo: Modo
  enBoleto: boolean
  onBoleto: () => void
  ocupado: boolean
}) {
  const c = a.consejo!
  const jugado = a.partido.estado === 'JUGADO' && a.partido.golesLocal !== null
  const fallo = jugado && c.acerto === false
  const verde = !fallo
  const insignia = jugado ? (c.acerto === null ? 'Sin dato' : c.acerto ? '✓ Acertó' : '✗ Falló') : c.confianza
  // Solo se puede agregar antes de que empiece (el backend lo vuelve a comprobar)
  const puedeAgregar = a.partido.estado === 'HOY' || a.partido.estado === 'PROXIMO'
  const mercado = a.categorias.flatMap((x) => x.mercados).find((m) => m.clave === c.clave)

  return (
    <section className={`${s.consejo} ${verde ? s.consejoVerde : s.consejoRojo}`}>
      <div className={s.consejoCabecera}>
        <span className={s.consejoTitulo} style={{ color: verde ? 'var(--verde-texto)' : 'var(--rojo-texto)' }}>
          {c.blindado && <IconoEscudo />} {jugado ? 'Consejo previo' : c.blindado ? 'Pick blindado' : `Consejo · ${MODOS[modo].nombre}`}
        </span>
        <span className={s.insignia} style={{ background: verde ? COLOR.verde : COLOR.rojo, color: verde ? 'var(--verde-fondo)' : 'var(--rojo-fondo)' }}>
          {insignia}
        </span>
      </div>
      <div className={s.consejoFila}>
        <span className={s.consejoTexto}>{c.texto}</span>
        <span className={s.consejoDerecha}>
          <span className={s.consejoPct} style={{ color: verde ? COLOR.verde : COLOR.rojo }}>{c.pct}%</span>
          <span className={s.cuota}>cuota ~{c.cuota.toFixed(2)}</span>
        </span>
      </div>
      {a.porQue && <p className={s.porQue}>{a.porQue}</p>}
      {mercado && mercado.pruebas.length > 1 && <Pruebas pruebas={mercado.pruebas} />}
      {puedeAgregar && (
        <button type="button" className={`${s.seguir} ${enBoleto ? s.siguiendo : ''}`} onClick={onBoleto}
          disabled={ocupado} aria-pressed={enBoleto}>
          {enBoleto ? 'En tu boleto ✓' : 'Agregar al boleto'}
        </button>
      )}
    </section>
  )
}

function Mini({ p, verde }: { p: PickBreve; verde: boolean }) {
  return (
    <div className={s.mini} style={{ background: verde ? 'var(--verde-fondo)' : 'var(--rojo-fondo)' }}>
      <span className={s.miniTexto}>{p.texto}</span>
      <span className={s.miniDerecha}>
        <span className={s.miniPct} style={{ color: verde ? COLOR.verde : COLOR.rojo }}>{p.pct}%</span>
        <span className={s.miniCuota}>~{p.cuota.toFixed(2)}</span>
      </span>
    </div>
  )
}

/** "También probable" (verde) y "Evita · poco probable" (rojo), en dos columnas. */
export function TambienEvita({ a }: { a: Analisis }) {
  return (
    <div className={s.dosColumnas}>
      <div className={s.columnaPicks}>
        <h2 className={s.columnaTitulo} style={{ color: COLOR.verde, margin: 0 }}>También probable</h2>
        {a.tambien.map((p) => <Mini key={p.clave} p={p} verde />)}
      </div>
      <div className={s.columnaPicks}>
        <h2 className={s.columnaTitulo} style={{ color: COLOR.rojo, margin: 0 }}>Evita · poco probable</h2>
        {a.evita.map((p) => <Mini key={p.clave} p={p} verde={false} />)}
      </div>
    </div>
  )
}
