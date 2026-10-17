import type { Modo } from '../api/tipos'

// Rangos de certeza de cada modo (los mismos que usa el motor del backend).
export const MODOS: Record<Modo, { nombre: string; desc: string }> = {
  SEGURO: { nombre: 'Seguro', desc: '78% a 97% · más aciertos, cuotas bajas' },
  EQUILIBRADO: { nombre: 'Equilibrado', desc: '62% a 88% · acierto y cuota' },
  ARRIESGADO: { nombre: 'Arriesgado', desc: '50% a 75% · cuotas altas, más riesgo' },
}

export const LISTA_MODOS: Modo[] = ['SEGURO', 'EQUILIBRADO', 'ARRIESGADO']
