import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { authApi } from '../api/auth'

/** Qué se le cuenta a quien espera. */
export type EstadoServidor = 'listo' | 'probando' | 'despertando' | 'sin_base_de_datos'

/** Cada intento espera como mucho esto: Render dormido a veces deja la petición colgada. */
const LIMITE_INTENTO_MS = 25_000

/**
 * Llama a /api/health al abrir la app y no se rinde: el servidor gratis (Render) se duerme y tarda
 * hasta ~3 minutos en despertar. Si responde pero sin base de datos (Aiven apagada), sigue esperando
 * y lo dice. Cuando todo está listo, vuelve a pedir la cuenta (esa petición falló mientras dormía).
 */
export function useServidorListo() {
  const queryClient = useQueryClient()
  const [estado, setEstado] = useState<EstadoServidor>('probando')
  const [segundos, setSegundos] = useState(0)

  useEffect(() => {
    let activo = true
    const inicio = Date.now()
    const aviso = setTimeout(() => activo && setEstado((e) => (e === 'probando' ? 'despertando' : e)), 1500)
    const reloj = setInterval(() => activo && setSegundos(Math.round((Date.now() - inicio) / 1000)), 1000)

    async function probar() {
      while (activo) {
        try {
          const r = await conLimite(authApi.salud(), LIMITE_INTENTO_MS)
          if (!activo) return
          if (r.estado === 'ok') {
            setEstado('listo')
            queryClient.resetQueries({ queryKey: ['me'] })
            return
          }
          setEstado('sin_base_de_datos')
        } catch {
          // Sigue dormido o sin red: se vuelve a probar
        }
        await new Promise((r) => setTimeout(r, 3000))
      }
    }
    probar()
    return () => {
      activo = false
      clearTimeout(aviso)
      clearInterval(reloj)
    }
  }, [queryClient])

  return { listo: estado === 'listo', estado, segundos }
}

function conLimite<T>(promesa: Promise<T>, ms: number): Promise<T> {
  return Promise.race([promesa, new Promise<T>((_, no) => setTimeout(() => no(new Error('tiempo')), ms))])
}
