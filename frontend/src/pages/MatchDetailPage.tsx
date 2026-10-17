import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'
import { partidosApi } from '../api/partidos'
import { useSesion } from '../auth/sesion'
import { EquiposRacha } from '../components/detalle/EquiposRacha'
import { CompareBars, HeadToHead, KeyPlayers, MarketTabs } from '../components/detalle/Historial'
import { AdviceCard, TambienEvita, WinProbability } from '../components/detalle/Prediccion'
import s from '../components/detalle/Detalle.module.css'
import { Aviso, ErrorCaja } from '../components/Estados'
import { IconoEstrella, IconoVolver } from '../components/iconos'
import { MatchCardSkeleton } from '../components/MatchCard'
import { useBoleto } from '../hooks/useBoleto'
import { useFavorito } from '../hooks/useFavorito'
import { etiquetaFecha } from '../lib/formato'
import ui from '../styles/ui.module.css'

export function MatchDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const { usuario } = useSesion()
  const favorito = useFavorito()

  const q = useQuery({ queryKey: ['analisis', id], queryFn: () => partidosApi.analisis(id), enabled: Number.isFinite(id) })

  const boleto = useBoleto()

  const volver = () => (window.history.length > 1 ? navigate(-1) : navigate('/'))
  const a = q.data

  return (
    <>
      <div className={s.barraSuperior}>
        <button type="button" className={s.botonIcono} onClick={volver} aria-label="Volver">
          <IconoVolver />
        </button>
        <div className={s.fechaLiga}>{a ? `${etiquetaFecha(a.partido.fecha, a.partido.estado)} · ${a.partido.liga.nombre}` : ''}</div>
        {a && (
          <button type="button" className={s.botonIcono} onClick={() => favorito.mutate(a.partido)}
            aria-label={a.partido.favorito ? 'Quitar de favoritos' : 'Agregar a favoritos'} aria-pressed={a.partido.favorito}>
            <IconoEstrella llena={a.partido.favorito} />
          </button>
        )}
      </div>

      <main className={s.cuerpo}>
        {q.isPending && (
          <>
            <MatchCardSkeleton />
            <MatchCardSkeleton />
          </>
        )}
        {q.isError && <ErrorCaja mensaje={q.error.message} onReintentar={() => q.refetch()} />}

        {a && (
          <>
            {a.aviso && <div className={s.anchoCompleto}><Aviso texto={a.aviso} /></div>}
            <div className={s.columnaA}>
              <EquiposRacha a={a} />
              {!a.datosSuficientes ? (
                <div className={ui.tarjeta}>
                  <h2 className={ui.tituloGrande}>Datos insuficientes</h2>
                  <span className={ui.sub} style={{ fontSize: 13, lineHeight: 1.5 }}>{a.mensaje}</span>
                </div>
              ) : (
                <>
                  <WinProbability a={a} />
                  {a.consejo && usuario && (
                    <AdviceCard a={a} modo={usuario.modo} enBoleto={boleto.tiene(id, a.consejo.clave)}
                      onBoleto={() => boleto.alternar.mutate({ partidoId: id, clave: a.consejo!.clave })}
                      ocupado={boleto.alternar.isPending} />
                  )}
                  <TambienEvita a={a} />
                </>
              )}
              <HeadToHead a={a} />
            </div>
            <div className={s.columnaB}>
              {a.datosSuficientes && (
                <>
                  <MarketTabs a={a} />
                  <CompareBars a={a} />
                  <KeyPlayers a={a} />
                </>
              )}
              <p className={ui.pie}>
                Poisson con Dixon-Coles · últimos 10 partidos, localía y liga · Cuotas aproximadas.
                Datos: API-Football, football-data.org y TheSportsDB · +18
              </p>
            </div>
          </>
        )}
      </main>
    </>
  )
}
