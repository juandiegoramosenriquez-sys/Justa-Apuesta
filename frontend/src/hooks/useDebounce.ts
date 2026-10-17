import { useEffect, useState } from 'react'

/** El valor, pero solo después de que deja de cambiar por "ms" milisegundos. */
export function useDebounce<T>(valor: T, ms = 300): T {
  const [v, setV] = useState(valor)
  useEffect(() => {
    const t = setTimeout(() => setV(valor), ms)
    return () => clearTimeout(t)
  }, [valor, ms])
  return v
}
