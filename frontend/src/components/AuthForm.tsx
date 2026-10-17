import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '../api/auth'
import type { Modo } from '../api/tipos'
import { useSesion } from '../auth/sesion'
import { LISTA_MODOS, MODOS } from '../lib/modos'
import { IconoGoogle } from './iconos'
import { LogoIcono, LogoTexto } from './Logo'
import s from './AuthForm.module.css'

type Tipo = 'login' | 'registro'

/** Pantalla de login y de registro (son la misma, con campos extra en registro). */
export function AuthForm({ tipo }: { tipo: Tipo }) {
  const registro = tipo === 'registro'
  const navigate = useNavigate()
  const { iniciar } = useSesion()

  const [nombre, setNombre] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [modo, setModo] = useState<Modo>('SEGURO')
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')

  const envio = useMutation({
    mutationFn: () =>
      registro
        ? authApi.registro({ nombre: nombre.trim(), email: email.trim(), password, modo })
        : authApi.login({ email: email.trim(), password }),
    onSuccess: (r) => {
      iniciar(r)
      navigate('/', { replace: true })
    },
    onError: (e) => setError(e.message),
  })

  // Mismas validaciones y en el mismo orden que el prototipo
  function validar(): string {
    if (registro && !nombre.trim()) return 'Escribe tu nombre.'
    if (email.indexOf('@') < 1) return 'Escribe un correo válido.'
    if (password.length < 6) return 'La contraseña debe tener al menos 6 caracteres.'
    return ''
  }

  function enviar(e: FormEvent) {
    e.preventDefault()
    setAviso('')
    const msg = validar()
    setError(msg)
    if (!msg) envio.mutate()
  }

  // Al escribir, el error se borra (como en el prototipo)
  const cambiar = (set: (v: string) => void) => (v: string) => {
    set(v)
    setError('')
  }

  return (
    <main className={s.pantalla}>
      <div className={s.marca}>
        <LogoIcono tamano="grande" />
        <h1 className="solo-lectores">Justa Apuesta</h1>
        <LogoTexto tamano="grande" />
        <div className={s.subtitulo}>
          {registro
            ? 'Crea tu cuenta y recibe consejos a tu medida.'
            : 'Entra para ver tus picks, favoritos y estadísticas.'}
        </div>
      </div>

      <form className={s.formulario} onSubmit={enviar} noValidate>
        {registro && (
          <label className={s.campo}>
            Nombre
            <input className={s.input} type="text" value={nombre} onChange={(e) => cambiar(setNombre)(e.target.value)}
              placeholder="Tu nombre" autoComplete="name" maxLength={80} />
          </label>
        )}
        <label className={s.campo}>
          Correo
          <input className={s.input} type="email" value={email} onChange={(e) => cambiar(setEmail)(e.target.value)}
            placeholder="tucorreo@gmail.com" autoComplete="email" inputMode="email" maxLength={120} />
        </label>
        <label className={s.campo}>
          Contraseña
          <input className={s.input} type="password" value={password} onChange={(e) => cambiar(setPassword)(e.target.value)}
            placeholder="Mínimo 6 caracteres" autoComplete={registro ? 'new-password' : 'current-password'} maxLength={72} />
        </label>

        {registro && (
          <div className={s.modos} role="radiogroup" aria-label="¿Cómo quieres tus consejos?">
            <span className={s.modosTitulo}>¿Cómo quieres tus consejos?</span>
            <div className={s.modosGrid}>
              {LISTA_MODOS.map((m) => (
                <button key={m} type="button" role="radio" aria-checked={modo === m}
                  className={`${s.modo} ${modo === m ? s.modoActivo : ''}`} onClick={() => setModo(m)}>
                  {MODOS[m].nombre}
                </button>
              ))}
            </div>
          </div>
        )}

        {error && <div className={s.error} role="alert">{error}</div>}
        {aviso && <div className={s.aviso} role="status">{aviso}</div>}

        <button type="submit" className={s.enviar} disabled={envio.isPending}>
          {registro ? 'Crear cuenta' : 'Entrar'}
        </button>
        <button type="button" className={s.google} onClick={() => { setError(''); setAviso('Próximamente') }}>
          <IconoGoogle />
          Continuar con Google
        </button>
      </form>

      <button type="button" className={s.cambiar} onClick={() => navigate(registro ? '/login' : '/registro')}>
        {registro ? '¿Ya tienes cuenta? Inicia sesión' : '¿No tienes cuenta? Crear cuenta'}
      </button>
      <div className={s.pie}>Solo para mayores de 18 años. Juega con responsabilidad.</div>
    </main>
  )
}
