import { api } from './cliente'
import type { Analisis, Boleto, ListaPartidos, Pestana, PickSeguido, PicksDelDia, Precision, Ultimos } from './tipos'

export const partidosApi = {
  lista: (estado: Pestana) => api<ListaPartidos>(`/partidos?estado=${estado}`),
  buscar: (q: string) => api<ListaPartidos>(`/buscar?q=${encodeURIComponent(q)}`),
  analisis: (id: number) => api<Analisis>(`/partidos/${id}/analisis`),
  ultimos: (equipoId: number) => api<Ultimos>(`/equipos/${equipoId}/ultimos?n=5`),
  ligas: () => api<string[]>('/ligas'),
}

export const picksApi = {
  delDia: (estado: 'HOY' | 'PROXIMO') => api<PicksDelDia>(`/picks?estado=${estado}`),
  /** null si todavía no se midió */
  precision: () => api<Precision | null>('/precision'),
  favoritos: () => api<ListaPartidos>('/favoritos'),
  marcarFavorito: (id: number) => api<void>(`/favoritos/${id}`, { method: 'POST' }),
  quitarFavorito: (id: number) => api<void>(`/favoritos/${id}`, { method: 'DELETE' }),
  seguidos: () => api<PickSeguido[]>('/picks-seguidos'),
  seguir: (partidoId: number, mercadoClave: string) =>
    api<PickSeguido>('/picks-seguidos', { method: 'POST', body: { partidoId, mercadoClave } }),
  dejarDeSeguir: (partidoId: number, mercadoClave: string) =>
    api<void>(`/picks-seguidos?partidoId=${partidoId}&mercadoClave=${encodeURIComponent(mercadoClave)}`, {
      method: 'DELETE',
    }),
  vaciarBoleto: () => api<void>('/picks-seguidos/pendientes', { method: 'DELETE' }),
  compartirBoleto: () => api<Boleto>('/boletos', { method: 'POST' }),
  boletoPublico: (codigo: string) => api<Boleto>(`/publico/boletos/${encodeURIComponent(codigo)}`, { sesion: false }),
}
