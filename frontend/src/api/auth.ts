import { api } from './cliente'
import type { Acento, AuthRespuesta, LoginDatos, Modo, RegistroDatos, Tema, Usuario } from './tipos'

export const authApi = {
  login: (datos: LoginDatos) => api<AuthRespuesta>('/auth/login', { method: 'POST', body: datos, sesion: false }),
  registro: (datos: RegistroDatos) => api<AuthRespuesta>('/auth/registro', { method: 'POST', body: datos, sesion: false }),
  me: () => api<Usuario>('/me'),
  actualizarMe: (cambios: { modo?: Modo; tipVisto?: boolean; ligasFavoritas?: string[]; tema?: Tema; acento?: Acento }) =>
    api<Usuario>('/me', { method: 'PATCH', body: cambios }),
  salud: () => api<{ estado: string }>('/health', { sesion: false }),
}
