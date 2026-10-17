import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { picksApi } from '../api/partidos'
import { ErrorCaja } from '../components/Estados'
import { LogoIcono, LogoTexto } from '../components/Logo'
import { MatchCardSkeleton } from '../components/MatchCard'
import { etiquetaFecha } from '../lib/formato'
import ui from '../styles/ui.module.css'
import s from './BoletoPublico.module.css'

/** Boleto compartido por link: se ve sin cuenta. */
export function BoletoPublicoPage() {
  const codigo = useParams().codigo ?? ''
  const q = useQuery({ queryKey: ['boleto', codigo], queryFn: () => picksApi.boletoPublico(codigo), retry: false })
  const b = q.data

  return (
    <main className={s.pagina}>
      <div className={s.marca}>
        <LogoIcono tamano="cabecera" />
        <LogoTexto tamano="cabecera" />
      </div>
      {q.isPending && <MatchCardSkeleton />}
      {q.isError && <ErrorCaja mensaje={q.error.message} onReintentar={() => q.refetch()} />}
      {b && (
        <article className={s.boleto}>
          <div className={s.cabecera}>
            <span className={s.titulo}>Boleto{b.autor ? ` de ${b.autor.split(' ')[0]}` : ''}</span>
            <span className={ui.sub}>{b.picks.length} {b.picks.length === 1 ? 'pick' : 'picks'}</span>
          </div>
          <div className={s.linea} />
          {b.picks.map((p, i) => (
            <div key={i} className={s.pick}>
              <div className={s.textos}>
                <span className={s.texto}>{p.texto}</span>
                <span className={ui.sub}>{p.partido}{p.fecha ? ` · ${etiquetaFecha(p.fecha, 'PROXIMO')}` : ''}</span>
              </div>
              <div className={s.derecha}>
                <span className={s.pct}>{p.pct}%</span>
                <span className={s.cuota}>~{p.cuota.toFixed(2)}</span>
              </div>
            </div>
          ))}
          <div className={s.linea} />
          <div className={s.total}><span>Probabilidad total</span><span className={s.grande}>{b.pct}%</span></div>
          <div className={s.total}><span>Cuota aproximada</span><span className={s.grandeBlanco}>~{b.cuota.toFixed(2)}</span></div>
        </article>
      )}
      <Link to="/" className={s.boton}>Abrir Justa Apuesta</Link>
      <p className={s.nota}>Análisis con datos reales · No es una apuesta · +18</p>
    </main>
  )
}
