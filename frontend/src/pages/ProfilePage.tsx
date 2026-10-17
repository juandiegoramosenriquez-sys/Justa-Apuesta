import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { authApi } from '../api/auth'
import { partidosApi, picksApi } from '../api/partidos'
import type { Acento, EstadoPick, Modo, Tema, Usuario } from '../api/tipos'
import { useSesion } from '../auth/sesion'
import { ErrorCaja } from '../components/Estados'
import { LISTA_MODOS, MODOS } from '../lib/modos'
import ui from '../styles/ui.module.css'
import s from './Paginas.module.css'

const TEMAS: { valor: Tema; texto: string }[] = [
  { valor: 'OSCURO', texto: 'Oscuro' },
  { valor: 'CLARO', texto: 'Claro' },
  { valor: 'AUTO', texto: 'Auto' },
]

/** Color de muestra de cada acento (el mismo en claro y oscuro). */
const ACENTOS: { valor: Acento; texto: string; color: string }[] = [
  { valor: 'LIMON', texto: 'Limón', color: '#C6F432' },
  { valor: 'VERDE', texto: 'Verde', color: '#2EE58A' },
  { valor: 'CELESTE', texto: 'Celeste', color: '#38BDF8' },
  { valor: 'MORADO', texto: 'Morado', color: '#A78BFA' },
  { valor: 'NARANJA', texto: 'Naranja', color: '#FB923C' },
]

const ESTADO: Record<EstadoPick, { texto: string; fondo: string; color: string }> = {
  ACERTADO: { texto: 'Acertado', fondo: 'var(--verde-fondo)', color: 'var(--verde)' },
  FALLADO: { texto: 'Fallado', fondo: 'var(--rojo-fondo)', color: 'var(--rojo)' },
  PENDIENTE: { texto: 'Pendiente', fondo: 'var(--acento-fondo-2)', color: 'var(--acento-texto-claro)' },
}

export function ProfilePage() {
  const { usuario, cerrar } = useSesion()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const ligas = useQuery({ queryKey: ['ligas'], queryFn: partidosApi.ligas, staleTime: Infinity })
  const picks = useQuery({ queryKey: ['picks-seguidos'], queryFn: picksApi.seguidos })

  const actualizar = useMutation({
    mutationFn: authApi.actualizarMe,
    onMutate: (c) => qc.setQueryData<Usuario>(['me'], (u) => u && { ...u, ...c }),
    onSuccess: (u, c) => {
      qc.setQueryData(['me'], u)
      // Cambiar el modo cambia los consejos en toda la app
      if (c.modo) {
        qc.invalidateQueries({ queryKey: ['partidos'] })
        qc.invalidateQueries({ queryKey: ['analisis'] })
        qc.invalidateQueries({ queryKey: ['picks'] })
      }
      if (c.ligasFavoritas) qc.invalidateQueries({ queryKey: ['partidos'] })
    },
    onError: () => qc.invalidateQueries({ queryKey: ['me'] }),
  })

  if (!usuario) return null
  const e = usuario.estadisticas
  const nombre = usuario.nombre.trim() || usuario.email.split('@')[0]
  const alternarLiga = (l: string) => {
    const actuales = usuario.ligasFavoritas
    actualizar.mutate({ ligasFavoritas: actuales.includes(l) ? actuales.filter((x) => x !== l) : [...actuales, l] })
  }

  return (
    <main className={`${ui.contenido} ${s.perfilPagina}`}>
      <h1 className="solo-lectores">Perfil</h1>

      <div className={s.perfilColumna}>
        <section className={s.perfil}>
          <div className={s.perfilCabecera}>
            <div className={s.avatar}>{nombre.charAt(0).toUpperCase()}</div>
            <div className={s.textos} style={{ flex: 1 }}>
              <span className={s.perfilNombre}>{nombre}</span>
              <span className={s.perfilEmail}>{usuario.email}</span>
            </div>
          </div>
          <div className={s.cifras} aria-label="Tus estadísticas">
            <div className={s.cifra}>
              <span className={s.cifraNumero}>{e.seguidos}</span>
              <span className={s.cifraTexto}>Picks</span>
            </div>
            <div className={s.cifra}>
              <span className={s.cifraNumero} style={{ color: 'var(--verde)' }}>{e.acertados}</span>
              <span className={s.cifraTexto}>Acertados</span>
            </div>
            <div className={s.cifra}>
              <span className={s.cifraNumero} style={{ color: 'var(--acento-texto)' }}>{e.porcentajeAcierto}%</span>
              <span className={s.cifraTexto}>Acierto</span>
            </div>
          </div>
        </section>

        <section className={ui.seccion}>
          <h2 className={ui.tituloSeccion}>Modo</h2>
          <div className={s.selectorModo} role="radiogroup" aria-label="Modo de consejos">
            {LISTA_MODOS.map((m: Modo) => (
              <button key={m} type="button" role="radio" aria-checked={usuario.modo === m}
                className={`${s.opcionModo} ${usuario.modo === m ? s.opcionModoActiva : ''}`}
                onClick={() => usuario.modo !== m && actualizar.mutate({ modo: m })}>
                {MODOS[m].nombre}
              </button>
            ))}
          </div>
          <span className={ui.sub}>{MODOS[usuario.modo].desc}</span>
        </section>

        <section className={ui.seccion}>
          <h2 className={ui.tituloSeccion}>Apariencia</h2>
          <div className={s.selectorModo} role="radiogroup" aria-label="Tema">
            {TEMAS.map((t) => (
              <button key={t.valor} type="button" role="radio" aria-checked={usuario.tema === t.valor}
                className={`${s.opcionModo} ${usuario.tema === t.valor ? s.opcionModoActiva : ''}`}
                onClick={() => usuario.tema !== t.valor && actualizar.mutate({ tema: t.valor })}>
                {t.texto}
              </button>
            ))}
          </div>
          {usuario.tema === 'AUTO' && <span className={ui.sub}>Sigue el modo claro u oscuro de tu aparato.</span>}
          <div className={s.colores} role="radiogroup" aria-label="Color">
            {ACENTOS.map((c) => (
              <button key={c.valor} type="button" role="radio" aria-checked={usuario.acento === c.valor}
                className={`${s.color} ${usuario.acento === c.valor ? s.colorActivo : ''}`}
                onClick={() => usuario.acento !== c.valor && actualizar.mutate({ acento: c.valor })}>
                <span className={s.muestra} style={{ background: c.color }} aria-hidden="true" />
                {c.texto}
              </button>
            ))}
          </div>
        </section>

        <section className={ui.seccion}>
          <h2 className={ui.tituloSeccion}>Ligas favoritas</h2>
          <div className={s.chips}>
            {(ligas.data ?? []).map((l) => {
              const activa = usuario.ligasFavoritas.includes(l)
              return (
                <button key={l} type="button" className={`${s.chip} ${activa ? s.chipActivo : ''}`} aria-pressed={activa}
                  onClick={() => alternarLiga(l)}>
                  {l}
                </button>
              )
            })}
          </div>
        </section>

        <button type="button" className={s.salir} onClick={cerrar}>Cerrar sesión</button>
      </div>

      <section className={`${ui.seccion} ${s.perfilColumna}`}>
        <h2 className={ui.tituloSeccion}>Historial de picks</h2>
        {picks.isError && <ErrorCaja mensaje={picks.error.message} onReintentar={() => picks.refetch()} />}
        {picks.data?.length === 0 && <span className={ui.sub}>Sin picks todavía.</span>}
        <div className={s.listaPicks}>
          {picks.data?.map((h) => (
            <button key={h.id} type="button" className={s.miPick} onClick={() => navigate(`/partido/${h.partidoId}`)}>
              <div className={s.textos} style={{ flex: 1 }}>
                <span style={{ fontSize: 15, fontWeight: 700 }}>{h.texto}</span>
                <span className={ui.sub}>{h.partido} · {h.probabilidad}% · ~{h.cuota.toFixed(2)}</span>
              </div>
              <span className={s.estadoPick} style={{ background: ESTADO[h.estado].fondo, color: ESTADO[h.estado].color }}>
                {ESTADO[h.estado].texto}
              </span>
            </button>
          ))}
        </div>
      </section>
    </main>
  )
}
