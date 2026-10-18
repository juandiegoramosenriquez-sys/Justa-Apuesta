import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { picksApi } from '../api/partidos'
import { useSesion } from '../auth/sesion'
import { ErrorCaja } from '../components/Estados'
import { Header } from '../components/Header'
import { MatchCardSkeleton } from '../components/MatchCard'
import { Segments } from '../components/Segments'
import { IconoCheck, IconoEscudo, IconoMas } from '../components/iconos'
import { useBoleto } from '../hooks/useBoleto'
import { etiquetaFecha } from '../lib/formato'
import ui from '../styles/ui.module.css'
import s from './Paginas.module.css'

type Dia = 'HOY' | 'PROXIMO'
const DIAS: { valor: Dia; texto: string }[] = [
  { valor: 'HOY', texto: 'Hoy' },
  { valor: 'PROXIMO', texto: 'Próximos' },
]

export function PicksPage() {
  const { usuario } = useSesion()
  const navigate = useNavigate()
  const [dia, setDia] = useState<Dia>('HOY')
  const q = useQuery({ queryKey: ['picks', dia], queryFn: () => picksApi.delDia(dia) })
  const boleto = useBoleto()
  const precision = useQuery({ queryKey: ['precision'], queryFn: picksApi.precision, staleTime: 30 * 60_000 })
  const acierto = precision.data?.blindados
  if (!usuario) return null

  const nombreDia = dia === 'HOY' ? 'Hoy' : 'Próximos días'
  const combinadas = q.data?.combinadas ?? []

  return (
    <>
      <Header usuario={usuario} onModo={() => navigate('/perfil')} />
      <Segments opciones={DIAS} activa={dia} onCambio={setDia} />
      <main className={ui.contenido} style={{ gap: 14 }}>
        {q.isPending && [0, 1].map((i) => <MatchCardSkeleton key={i} />)}
        {q.isError && <ErrorCaja mensaje={q.error.message} onReintentar={() => q.refetch()} />}

        {q.data && (
          <div className={ui.filaTitulo}>
            <h1 className={ui.tituloSeccion}>Combinadas seguras · {nombreDia}</h1>
            <span className={ui.sub}>Solo picks blindados</span>
          </div>
        )}
        {acierto && acierto.picks >= 20 && (
          <div className={s.acierto}>
            <span className={s.aciertoPct}>{acierto.pct}%</span>
            <span className={s.aciertoTexto}>
              <b>Acierto real del pick blindado</b>
              <span>{acierto.acertados} de {acierto.picks} en partidos ya jugados, analizados con los datos de antes de cada partido. Ningún pick es seguro al 100%.</span>
            </span>
          </div>
        )}
        {q.data && combinadas.length === 0 && (
          <div className={ui.vacio}>Sin picks blindados {dia === 'HOY' ? 'para hoy' : 'en los próximos días'}.</div>
        )}
        <div className={s.combinadas}>
          {combinadas.map((c) => {
            const todas = c.picks.every((p) => boleto.tiene(p.partidoId, p.clave))
            return (
              <section key={c.nivel} className={`${s.combo} ${s[c.nivel]}`}>
                <div className={s.comboCabecera}>
                  <h2 className={s.comboTitulo}>{c.nombre}</h2>
                  <span className={s.cuota}>~{c.cuota.toFixed(2)}</span>
                </div>
                <span className={ui.sub}>{c.picks.length === 1 ? '1 partido' : `${c.picks.length} partidos distintos`}</span>
                {c.picks.map((p) => (
                  <button key={p.partidoId} type="button" className={s.comboPick} onClick={() => navigate(`/partido/${p.partidoId}`)}>
                    <div className={s.textos}>
                      <span className={ui.sub} style={{ fontSize: 11 }}>{p.partido} · {etiquetaFecha(p.fecha, 'PROXIMO')}</span>
                      <span style={{ fontSize: 14, fontWeight: 500 }}>{p.texto}</span>
                    </div>
                    <span className={s.pctVerde}>{p.pct}%</span>
                  </button>
                ))}
                <div className={s.comboTotal}>
                  <span style={{ fontSize: 13, color: 'var(--acento-texto-claro)' }}>Salen todos</span>
                  <span className={ui.numero} style={{ fontSize: 26, color: 'var(--acento-texto-claro)' }}>{c.pct}%</span>
                </div>
                <button type="button" className={`${s.agregarCombo} ${todas ? s.agregadaCombo : ''}`} disabled={todas || boleto.agregar.isPending}
                  onClick={() => boleto.agregar.mutate(c.picks.map((p) => ({ partidoId: p.partidoId, clave: p.clave })))}>
                  {todas ? 'En tu boleto ✓' : 'Agregar al boleto'}
                </button>
              </section>
            )
          })}
        </div>

        {q.data && q.data.mejores.length > 0 && (
          <>
            <h2 className={ui.tituloSeccion}>Mejores picks · {nombreDia}</h2>
            <div className={s.rankingLista}>
              {q.data.mejores.map((b, i) => (
                <div key={`${b.partidoId}-${b.clave}`} className={s.ranking}>
                  <span className={s.puesto}>{i + 1}</span>
                  <button type="button" className={s.textos} style={{ flex: 1, border: 0, background: 'transparent', color: 'inherit', padding: 0, textAlign: 'left' }}
                    onClick={() => navigate(`/partido/${b.partidoId}`)}>
                    <span style={{ fontSize: 14, fontWeight: 500, display: 'flex', alignItems: 'center', gap: 5 }}>
                      {b.blindado && <span style={{ color: 'var(--verde)', display: 'inline-flex' }} aria-label="Pick blindado"><IconoEscudo size={13} /></span>}
                      {b.texto}
                    </span>
                    <span className={ui.sub}>{b.partido} · {b.categoria}</span>
                  </button>
                  <span className={s.rankingDerecha}>
                    <span className={s.pctVerde}>{b.pct}%</span>
                    <span className={s.cuotaChica}>~{b.cuota.toFixed(2)}</span>
                  </span>
                  <button type="button" className={`${s.mas} ${boleto.tiene(b.partidoId, b.clave) ? s.masActivo : ''}`}
                    aria-label={boleto.tiene(b.partidoId, b.clave) ? `Quitar ${b.texto} del boleto` : `Agregar ${b.texto} al boleto`}
                    aria-pressed={boleto.tiene(b.partidoId, b.clave)} disabled={boleto.alternar.isPending}
                    onClick={() => boleto.alternar.mutate({ partidoId: b.partidoId, clave: b.clave })}>
                    {boleto.tiene(b.partidoId, b.clave) ? <IconoCheck size={14} /> : <IconoMas size={14} />}
                  </button>
                </div>
              ))}
            </div>
          </>
        )}
      </main>
    </>
  )
}
