import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { authApi } from '../api/auth'
import { configurarAlNoAutorizado } from '../api/cliente'
import { aplicarApariencia } from '../lib/apariencia'
import type { AuthRespuesta } from '../api/tipos'
import { borrarToken, guardarToken, leerToken } from './almacen'
import { SesionContext, type Sesion } from './sesion'

export function SesionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [token, setToken] = useState<string | null>(() => leerToken())

  const cerrar = useCallback(() => {
    borrarToken()
    setToken(null)
    queryClient.clear()
  }, [queryClient])

  // Si el backend dice 401, la sesión se cierra y el router manda al login
  useEffect(() => configurarAlNoAutorizado(cerrar), [cerrar])

  const { data: usuario, isPending } = useQuery({
    queryKey: ['me'],
    queryFn: authApi.me,
    enabled: token !== null,
  })

  // La apariencia guardada en la cuenta manda (así se ve igual en todos sus aparatos)
  useEffect(() => {
    if (usuario?.tema && usuario.acento) aplicarApariencia(usuario.tema, usuario.acento)
  }, [usuario?.tema, usuario?.acento])

  const iniciar = useCallback(
    (r: AuthRespuesta) => {
      guardarToken(r.token)
      queryClient.setQueryData(['me'], r.usuario)
      setToken(r.token)
    },
    [queryClient],
  )

  const valor = useMemo<Sesion>(
    () => ({ token, usuario, cargandoUsuario: token !== null && isPending, iniciar, cerrar }),
    [token, usuario, isPending, iniciar, cerrar],
  )

  return <SesionContext.Provider value={valor}>{children}</SesionContext.Provider>
}
