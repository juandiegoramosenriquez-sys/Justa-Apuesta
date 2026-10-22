import { useQuery } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router'
import { partidosApi } from '../api/partidos'
import type { PartidoResumen, Pestana } from '../api/tipos'
import { useSesion } from '../auth/sesion'
import { Aviso, ErrorCaja, IndicadorJalar } from '../components/Estados'
import { Header } from '../components/Header'
import { MatchCard, MatchCardSkeleton } from '../components/MatchCard'
import { SearchBar } from '../components/SearchBar'
import { Segments } from '../components/Segments'
import { useDebounce } from '../hooks/useDebounce'
import { usePullToRefresh } from '../hooks/usePullToRefresh'
import { haceCuanto, paisEs, prioridadLiga } from '../lib/formato'
import ui from '../styles/ui.module.css'

const PESTANAS: { valor: Pestana; texto: string }[] = [
  { valor: 'JUGADO', texto: 'Jugados' },
  { valor: 'HOY', texto: 'Hoy' },
  { valor: 'PROXIMO', texto: 'Próximos' },
]
const NOMBRE: Record<Pestana, string> = { JUGADO: 'Jugados', HOY: 'Hoy', PROXIMO: 'Próximos' }

/** Agrupa por liga: primero las favoritas, luego las más importantes y luego el resto (por orden de llegada). */
function porLiga(partidos: PartidoResumen[], favoritas: Set<string>) {
  const grupos = new Map<string, PartidoResumen[]>()
  partidos.forEach((p) => {
    const clave = `${p.liga.nombre}|${p.liga.pais ?? ''}`
    grupos.set(clave, [...(grupos.get(clave) ?? []), p])
  })
  return [...grupos.values()].sort(
    (a, b) => prioridadLiga(a[0].liga.nombre, favoritas) - prioridadLiga(b[0].liga.nombre, favoritas),
  )
}

/** Búsqueda: primero los partidos por jugar y luego los jugados, cada grupo con su título. */
function porEstado(partidos: PartidoResumen[]) {
  const jugados = partidos.filter((p) => p.estado === 'JUGADO')
  const porJugar = partidos.filter((p) => p.estado !== 'JUGADO')
  return [
    { titulo: 'Próximos', lista: porJugar },
    { titulo: 'Jugados', lista: jugados },
  ].filter((g) => g.lista.length > 0)
}

/** Los partidos de los 2 días siguientes que tengan partidos (para no mostrar semanas enteras). */
function proximosPrimeros(partidos: PartidoResumen[]) {
  const dia = (p: PartidoResumen) => new Date(p.fecha).toLocaleDateString('es-PE')
  const ordenados = [...partidos].sort((a, b) => a.fecha.localeCompare(b.fecha))
  const dias = [...new Set(ordenados.map(dia))].slice(0, 2)
  return ordenados.filter((p) => dias.includes(dia(p)))
}

export function MatchesPage() {
  const { usuario } = useSesion()
  const navigate = useNavigate()
  // Pestaña y búsqueda en la URL: al volver del detalle se mantienen
  const [params, setParams] = useSearchParams()
  const pestana = (params.get('pestana') as Pestana) || 'HOY'
  const texto = params.get('q') ?? ''
  const q = useDebounce(texto.trim(), 300)
  const buscando = q.length >= 3

  const lista = useQuery({
    queryKey: buscando ? ['partidos', 'buscar', q] : ['partidos', pestana],
    queryFn: () => (buscando ? partidosApi.buscar(q) : partidosApi.lista(pestana)),
  })

  // Si hoy ya no quedan partidos, en vez de dejar la pestaña vacía se muestran los siguientes
  const hoyVacio = !buscando && pestana === 'HOY' && lista.data !== undefined && lista.data.partidos.length === 0
  const siguientes = useQuery({
    queryKey: ['partidos', 'PROXIMO'],
    queryFn: () => partidosApi.lista('PROXIMO'),
    enabled: hoyVacio,
  })

  const pull = usePullToRefresh(() => lista.refetch())

  if (!usuario) return null
  const favoritas = new Set(usuario.ligasFavoritas)
  const delDia = lista.data?.partidos ?? []
  const usandoSiguientes = hoyVacio && (siguientes.data?.partidos.length ?? 0) > 0
  const partidos = usandoSiguientes ? proximosPrimeros(siguientes.data!.partidos) : delDia
  const cambiar = (cambios: Record<string, string>) =>
    setParams((p) => {
      Object.entries(cambios).forEach(([k, v]) => (v ? p.set(k, v) : p.delete(k)))
      return p
    }, { replace: true })

  const actualizado = haceCuanto(lista.data?.actualizadoEn ?? null)

  return (
    <>
      <IndicadorJalar {...pull} />
      <Header usuario={usuario} onModo={() => navigate('/perfil')} />
      <SearchBar valor={texto} onCambio={(v) => cambiar({ q: v })} buscando={lista.isFetching && buscando} />
      {!buscando && <Segments opciones={PESTANAS} activa={pestana} onCambio={(v) => cambiar({ pestana: v })} />}

      <main className={ui.contenido}>
        <div className={ui.filaTitulo}>
          <h1 className={ui.tituloSeccion}>{buscando ? 'Resultados' : usandoSiguientes ? 'Partidos · Siguientes' : `Partidos · ${NOMBRE[pestana]}`}</h1>
          <span className={ui.sub}>
            {buscando ? '' : lista.data ? `${partidos.length} ${partidos.length === 1 ? 'partido' : 'partidos'}` : ''}
          </span>
        </div>

        {lista.data?.aviso && <Aviso texto={lista.data.aviso} />}
        {texto.trim().length > 0 && texto.trim().length < 3 && <span className={ui.sub}>Escribe al menos 3 letras.</span>}

        {(lista.isPending || (hoyVacio && siguientes.isPending)) && [0, 1, 2, 3].map((i) => <MatchCardSkeleton key={i} />)}
        {lista.isError && (
          <ErrorCaja mensaje={`No pudimos cargar los partidos. ${lista.error.message}`} onReintentar={() => lista.refetch()} />
        )}
        {usandoSiguientes && (
          <Aviso texto="Ya no quedan partidos por jugar hoy. Estos son los siguientes, así ya puedes ir viendo los picks." />
        )}
        {lista.data && partidos.length === 0 && !(hoyVacio && siguientes.isPending) && (
          <div className={ui.vacio}>
            {buscando ? 'Sin partidos para ese equipo.' : `Sin partidos ${NOMBRE[pestana].toLowerCase()}. Busca un equipo.`}
          </div>
        )}
        {buscando
          ? porEstado(partidos).map((g) => (
              <section key={g.titulo} className={ui.grupoLiga} aria-label={g.titulo}>
                <h2 className={ui.cabeceraLiga}>{g.titulo}</h2>
                {g.lista.map((p) => (
                  <MatchCard key={p.id} p={p} ligaFavorita={favoritas.has(p.liga.nombre)} />
                ))}
              </section>
            ))
          : porLiga(partidos, favoritas).map((grupo) => {
              const liga = grupo[0].liga
              return (
                <section key={`${liga.nombre}|${liga.pais ?? ''}`} className={ui.grupoLiga} aria-label={liga.nombre}>
                  <h2 className={ui.cabeceraLiga}>
                    {liga.logo && <img src={liga.logo} alt="" width={20} height={20} loading="lazy" />}
                    <span>{liga.nombre}</span>
                    {paisEs(liga.pais) && <span className={ui.paisLiga}>{paisEs(liga.pais)}</span>}
                    {favoritas.has(liga.nombre) && <span aria-label="Liga favorita">★</span>}
                  </h2>
                  {grupo.map((p) => (
                    <MatchCard key={p.id} p={p} ligaFavorita={favoritas.has(liga.nombre)} conLiga={false} />
                  ))}
                </section>
              )
            })}
        {actualizado && partidos.length > 0 && <span className={ui.sub}>Actualizado {actualizado}</span>}
      </main>
    </>
  )
}
