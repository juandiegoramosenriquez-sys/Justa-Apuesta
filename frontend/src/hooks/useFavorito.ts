import { useMutation, useQueryClient } from '@tanstack/react-query'
import { picksApi } from '../api/partidos'
import type { Analisis, ListaPartidos, PartidoResumen } from '../api/tipos'

/** Marca o quita un favorito, con la estrella actualizada al instante en todas las listas. */
export function useFavorito() {
  const qc = useQueryClient()

  const cambiar = (id: number, favorito: boolean) => {
    qc.setQueriesData<ListaPartidos>({ queryKey: ['partidos'] }, (l) =>
      l && { ...l, partidos: l.partidos.map((p) => (p.id === id ? { ...p, favorito } : p)) })
    qc.setQueriesData<Analisis>({ queryKey: ['analisis', id] }, (a) =>
      a && { ...a, partido: { ...a.partido, favorito } })
  }

  return useMutation({
    mutationFn: (p: PartidoResumen) => (p.favorito ? picksApi.quitarFavorito(p.id) : picksApi.marcarFavorito(p.id)),
    onMutate: (p) => cambiar(p.id, !p.favorito),
    onError: (_e, p) => cambiar(p.id, p.favorito),
    onSettled: () => qc.invalidateQueries({ queryKey: ['partidos', 'favoritos'] }),
  })
}
