import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { picksApi } from '../api/partidos'
import { useSesion } from '../auth/sesion'
import { ErrorCaja } from '../components/Estados'
import { Header } from '../components/Header'
import { MatchCard, MatchCardSkeleton } from '../components/MatchCard'
import ui from '../styles/ui.module.css'

export function FavoritesPage() {
  const { usuario } = useSesion()
  const navigate = useNavigate()
  const q = useQuery({ queryKey: ['partidos', 'favoritos'], queryFn: picksApi.favoritos })
  if (!usuario) return null
  const favoritas = new Set(usuario.ligasFavoritas)
  const partidos = q.data?.partidos ?? []

  return (
    <>
      <Header usuario={usuario} onModo={() => navigate('/perfil')} />
      <main className={ui.contenido}>
        <div className={ui.filaTitulo}>
          <h1 className={ui.tituloSeccion}>Tus favoritos</h1>
          {q.data && <span className={ui.sub}>{partidos.length} {partidos.length === 1 ? 'partido' : 'partidos'}</span>}
        </div>
        {q.isPending && [0, 1].map((i) => <MatchCardSkeleton key={i} />)}
        {q.isError && <ErrorCaja mensaje={q.error.message} onReintentar={() => q.refetch()} />}
        {q.data && partidos.length === 0 && (
          <div className={ui.vacio}>Toca la estrella de un partido para guardarlo aquí.</div>
        )}
        {partidos.map((p) => (
          <MatchCard key={p.id} p={p} ligaFavorita={favoritas.has(p.liga.nombre)} />
        ))}
      </main>
    </>
  )
}
