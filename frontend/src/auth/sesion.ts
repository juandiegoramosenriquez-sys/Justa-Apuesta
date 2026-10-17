import { createContext, useContext } from 'react'
import type { AuthRespuesta, Usuario } from '../api/tipos'

export interface Sesion {
  token: string | null
  usuario: Usuario | undefined
  cargandoUsuario: boolean
  iniciar: (respuesta: AuthRespuesta) => void
  cerrar: () => void
}

export const SesionContext = createContext<Sesion | null>(null)

export function useSesion(): Sesion {
  const s = useContext(SesionContext)
  if (!s) throw new Error('useSesion debe usarse dentro de <SesionProvider>')
  return s
}
