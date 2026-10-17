import { leerToken } from '../auth/almacen'

// En desarrollo usa el proxy de Vite (/api -> localhost:8080).
// En producción VITE_API_URL apunta al backend en Render.
const BASE = (import.meta.env.VITE_API_URL ?? '') + '/api'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, mensaje: string) {
    super(mensaje)
    this.status = status
  }
}

// Se llama cuando el backend responde 401 (token vencido o inválido)
let alNoAutorizado: (() => void) | null = null
export function configurarAlNoAutorizado(fn: () => void) {
  alNoAutorizado = fn
}

interface Opciones {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  /** false en login/registro: un 401 ahí es "contraseña incorrecta", no sesión vencida */
  sesion?: boolean
}

export async function api<T>(ruta: string, { method = 'GET', body, sesion = true }: Opciones = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = sesion ? leerToken() : null
  if (token) headers.Authorization = `Bearer ${token}`

  let res: Response
  try {
    res = await fetch(BASE + ruta, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new ApiError(0, 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo otra vez.')
  }

  const datos = await res.json().catch(() => null)
  if (!res.ok) {
    if (res.status === 401 && sesion) alNoAutorizado?.()
    throw new ApiError(res.status, datos?.mensaje ?? 'Algo salió mal. Inténtalo otra vez.')
  }
  return datos as T
}
