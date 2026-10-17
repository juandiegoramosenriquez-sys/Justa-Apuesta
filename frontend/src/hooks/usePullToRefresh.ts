import { useEffect, useRef, useState } from 'react'

const UMBRAL = 70

/**
 * "Jalar para actualizar" en el celular: si estás arriba del todo y deslizas hacia abajo más de 70 px,
 * llama a onRefrescar. Devuelve cuánto se jaló (para mostrar el indicador).
 */
export function usePullToRefresh(onRefrescar: () => Promise<unknown>) {
  const [jalado, setJalado] = useState(0)
  const [refrescando, setRefrescando] = useState(false)
  const inicio = useRef<number | null>(null)
  const distancia = useRef(0)
  const fn = useRef(onRefrescar)

  useEffect(() => {
    fn.current = onRefrescar
  }, [onRefrescar])

  useEffect(() => {
    const alTocar = (e: TouchEvent) => {
      inicio.current = window.scrollY <= 0 ? e.touches[0].clientY : null
      distancia.current = 0
    }
    const alMover = (e: TouchEvent) => {
      if (inicio.current === null) return
      const d = e.touches[0].clientY - inicio.current
      distancia.current = d > 0 ? Math.min(d, 110) : 0
      setJalado(distancia.current)
    }
    const alSoltar = () => {
      if (inicio.current === null) return
      inicio.current = null
      const d = distancia.current
      distancia.current = 0
      setJalado(0)
      if (d >= UMBRAL) {
        setRefrescando(true)
        fn.current().finally(() => setRefrescando(false))
      }
    }
    window.addEventListener('touchstart', alTocar, { passive: true })
    window.addEventListener('touchmove', alMover, { passive: true })
    window.addEventListener('touchend', alSoltar)
    return () => {
      window.removeEventListener('touchstart', alTocar)
      window.removeEventListener('touchmove', alMover)
      window.removeEventListener('touchend', alSoltar)
    }
  }, [])

  return { jalado, refrescando, listo: jalado >= UMBRAL }
}
