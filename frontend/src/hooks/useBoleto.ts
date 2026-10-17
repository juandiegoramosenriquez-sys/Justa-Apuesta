import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { picksApi } from '../api/partidos'
import type { PickSeguido } from '../api/tipos'

export interface NuevoPick {
  partidoId: number
  clave: string
}

/** Cuota de varios picks juntos (producto, 2 decimales). */
export function cuotaTotal(cuotas: number[]) {
  return Math.round(cuotas.reduce((a, b) => a * b, 1) * 100) / 100
}

/** Probabilidad (en %) de que salgan todos. */
export function pctTotal(pcts: number[]) {
  return Math.round(pcts.reduce((a, b) => a * (b / 100), 1) * 100)
}

/** El boleto del usuario: sus picks pendientes, guardados en su cuenta. */
export function useBoleto() {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['picks-seguidos'], queryFn: picksApi.seguidos })
  const picks: PickSeguido[] = (q.data ?? []).filter((p) => p.estado === 'PENDIENTE')

  const refrescar = () => {
    qc.invalidateQueries({ queryKey: ['picks-seguidos'] })
    qc.invalidateQueries({ queryKey: ['me'] })
    qc.invalidateQueries({ queryKey: ['analisis'] })
  }

  const tiene = (partidoId: number, clave: string) =>
    picks.some((p) => p.partidoId === partidoId && p.mercadoClave === clave)

  const alternar = useMutation({
    mutationFn: async ({ partidoId, clave }: NuevoPick) => {
      if (tiene(partidoId, clave)) await picksApi.dejarDeSeguir(partidoId, clave)
      else await picksApi.seguir(partidoId, clave)
    },
    onSettled: refrescar,
  })

  const agregar = useMutation({
    mutationFn: async (lista: NuevoPick[]) => {
      for (const p of lista.filter((x) => !tiene(x.partidoId, x.clave))) await picksApi.seguir(p.partidoId, p.clave)
    },
    onSettled: refrescar,
  })

  const vaciar = useMutation({ mutationFn: picksApi.vaciarBoleto, onSettled: refrescar })

  return {
    picks,
    tiene,
    alternar,
    agregar,
    vaciar,
    pct: pctTotal(picks.map((p) => p.probabilidad)),
    cuota: cuotaTotal(picks.map((p) => p.cuota)),
  }
}
