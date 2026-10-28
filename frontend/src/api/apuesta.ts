import { api } from './cliente'

export interface SeleccionLeida {
  leido: string
  partidoId: number | null
  partido: string | null
  fecha: string | null
  liga: string | null
  mercado: string | null
  pct: number | null
  cuota: number | null
  nota: string | null
}

export interface AnalisisApuesta {
  selecciones: SeleccionLeida[]
  pctTotal: number | null
  cuotaJusta: number | null
  consejo: string | null
}

export const apuestaApi = {
  analizar: (texto: string) => api<AnalisisApuesta>('/apuesta/analizar', { method: 'POST', body: { texto } }),
}
