import { useEffect, useState } from 'react'
import { authApi } from '../api/auth'

/**
 * Llama a /api/health al abrir la app. Si tarda más de 1.5 s (Render dormido),
 * "lento" pasa a true para mostrar "Despertando el servidor…". Reintenta hasta que responda.
 */
export function useServidorListo() {
  const [listo, setListo] = useState(false)
  const [lento, setLento] = useState(false)

  useEffect(() => {
    let activo = true
    const aviso = setTimeout(() => activo && setLento(true), 1500)

    async function probar() {
      while (activo) {
        try {
          await authApi.salud()
          if (activo) setListo(true)
          return
        } catch {
          await new Promise((r) => setTimeout(r, 3000))
        }
      }
    }
    probar()
    return () => {
      activo = false
      clearTimeout(aviso)
    }
  }, [])

  return { listo, lento }
}
