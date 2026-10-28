import { useState } from 'react'
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
import { IconoFlecha } from '../components/iconos'
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

/** Búsqueda: los partidos por jugar y los ya jugados, cada grupo desplegable. */
function porEstado(partidos: PartidoResumen[]) {
  const jugados = partidos.filter((p) => p.estado === 'JUGADO')
  const porJugar = partidos.filter((p) => p.estado !== 'JUGADO')
  const conDato = jugados.filter((p) => p.consejo?.acerto != null)
  const acertados = conDato.filter((p) => p.consejo?.acerto).length
  return [
    { clave: 'proximos', titulo: 'Próximos partidos', detalle: '', lista: porJugar },
    {
      clave: 'pasados',
      titulo: 'Partidos pasados',
      detalle: conDato.length > 0 ? `Acertó ${acertados} de ${conDato.length}` : '',
      lista: jugados,
    },
  ].filter((g) => g.lista.length > 0)
}

/** Un grupo de la búsqueda con flecha para abrirlo o cerrarlo. */
function GrupoDesplegable({ titulo, detalle, cantidad, abiertoAlInicio, children }: {
  titulo: string
  detalle: string
  cantidad: number
  abiertoAlInicio: boolean
  children: React.ReactNode
}) {
  const [abierto, setAbierto] = useState(abiertoAlInicio)
  return (
    <section className={ui.grupoLiga} aria-label={titulo}>
      <button type="button" className={ui.desplegable} aria-expanded={abierto} onClick={() => setAbierto(!abierto)}>
        <span className={ui.desplegableTitulo}>{titulo} <span className={ui.paisLiga}>({cantidad})</span></span>
        {detalle && <span className={ui.desplegableDetalle}>{detalle}</span>}
        <IconoFlecha size={18} arriba={abierto} />
      </button>
      {abierto && children}
    </section>
  )
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
    // Si el servidor sigue buscando ese equipo en otra fuente, se vuelve a pedir solo
    refetchInterval: (query) => (query.state.data?.buscandoMas ? 5000 : false),
  })

  const pull = usePullToRefresh(() => lista.refetch())

  if (!usuario) return null
  const favoritas = new Set(usuario.ligasFavoritas)
  const partidos = lista.data?.partidos ?? []
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
          <h1 className={ui.tituloSeccion}>{buscando ? 'Resultados' : `Partidos · ${NOMBRE[pestana]}`}</h1>
          <span className={ui.sub}>
            {buscando ? '' : lista.data ? `${partidos.length} ${partidos.length === 1 ? 'partido' : 'partidos'}` : ''}
          </span>
        </div>

        {lista.data?.aviso && <Aviso texto={lista.data.aviso} />}
        {buscando && lista.data?.buscandoMas && <Aviso texto="Buscando más partidos de este equipo en otras fuentes…" />}
        {texto.trim().length > 0 && texto.trim().length < 3 && <span className={ui.sub}>Escribe al menos 3 letras.</span>}

        {lista.isPending && [0, 1, 2, 3].map((i) => <MatchCardSkeleton key={i} />)}
        {lista.isError && (
          <ErrorCaja mensaje={`No pudimos cargar los partidos. ${lista.error.message}`} onReintentar={() => lista.refetch()} />
        )}
        {lista.data && partidos.length === 0 && (
          <div className={ui.vacio}>
            {buscando
              ? 'Sin partidos para ese equipo.'
              : pestana === 'HOY'
                ? 'Ya no quedan partidos por jugar hoy. Mira la pestaña Próximos o busca un equipo.'
                : `Sin partidos ${NOMBRE[pestana].toLowerCase()}. Busca un equipo.`}
          </div>
        )}
        {buscando
          ? porEstado(partidos).map((g, i, grupos) => (
              <GrupoDesplegable key={`${q}-${g.clave}`} titulo={g.titulo} detalle={g.detalle} cantidad={g.lista.length}
                abiertoAlInicio={g.clave === 'proximos' || grupos.length === 1 || i === 0}>
                {g.lista.map((p) => (
                  <MatchCard key={p.id} p={p} ligaFavorita={favoritas.has(p.liga.nombre)} />
                ))}
              </GrupoDesplegable>
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
