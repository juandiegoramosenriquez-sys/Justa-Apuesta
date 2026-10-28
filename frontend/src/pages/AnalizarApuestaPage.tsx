import { useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { apuestaApi } from '../api/apuesta'
import { Aviso, ErrorCaja } from '../components/Estados'
import { IconoImagen, IconoVolver } from '../components/iconos'
import { etiquetaFecha } from '../lib/formato'
import { leerFoto } from '../lib/leerFoto'
import ui from '../styles/ui.module.css'
import s from './AnalizarApuesta.module.css'

const EJEMPLO = 'Liverpool vs Brentford - Más de 2.5 goles\nReal Madrid vs Barcelona - Ambos marcan'

function colorPct(pct: number) {
  return pct >= 65 ? 'var(--verde)' : pct <= 35 ? 'var(--rojo)' : 'var(--texto)'
}

/** Analizar mi apuesta: foto o texto de un boleto armado en una casa de apuestas -> probabilidad de que salga. */
export function AnalizarApuestaPage() {
  const navigate = useNavigate()
  const archivo = useRef<HTMLInputElement>(null)
  const [texto, setTexto] = useState('')
  const [leyendo, setLeyendo] = useState<number | null>(null)
  const [errorFoto, setErrorFoto] = useState<string | null>(null)
  const analizar = useMutation({ mutationFn: apuestaApi.analizar })

  async function alElegirFoto(f: File | undefined) {
    if (!f) return
    setErrorFoto(null)
    setLeyendo(0)
    analizar.reset()
    try {
      const leido = (await leerFoto(f, setLeyendo)).trim()
      setTexto(leido)
      if (leido) analizar.mutate(leido)
      else setErrorFoto('No pudimos leer texto en la foto. Prueba con una captura más clara o escríbelo.')
    } catch {
      setErrorFoto('No pudimos leer la foto. Revisa tu conexión (la primera vez baja el lector) o escríbelo.')
    } finally {
      setLeyendo(null)
      if (archivo.current) archivo.current.value = ''
    }
  }

  const r = analizar.data

  return (
    <main className={ui.contenido} style={{ gap: 14 }}>
      <div className={s.cabecera}>
        <button type="button" className={s.volver} aria-label="Volver" onClick={() => navigate(-1)}>
          <IconoVolver />
        </button>
        <h1 className={ui.tituloSeccion}>Analizar mi apuesta</h1>
      </div>
      <p className={ui.sub} style={{ margin: 0, lineHeight: 1.5 }}>
        Sube una captura del boleto que armaste en tu casa de apuestas, o escríbelo. Te decimos la probabilidad de
        cada selección y de la combinada. La foto se lee en tu celular y no se guarda.
      </p>

      <input ref={archivo} type="file" accept="image/*" hidden onChange={(e) => alElegirFoto(e.target.files?.[0])} />
      <button type="button" className={s.subir} disabled={leyendo !== null} onClick={() => archivo.current?.click()}>
        <IconoImagen size={18} />
        {leyendo !== null ? `Leyendo la foto… ${leyendo}%` : 'Subir foto del boleto'}
      </button>
      {errorFoto && <ErrorCaja mensaje={errorFoto} />}

      <label className={s.etiqueta} htmlFor="texto-apuesta">
        Texto del boleto {texto && <span className={ui.sub}>· revisa y corrige si algo se leyó mal</span>}
      </label>
      <textarea id="texto-apuesta" className={s.texto} rows={7} value={texto} placeholder={EJEMPLO}
        onChange={(e) => setTexto(e.target.value)} />
      <button type="button" className={s.calcular} disabled={!texto.trim() || analizar.isPending}
        onClick={() => analizar.mutate(texto)}>
        {analizar.isPending ? 'Calculando…' : 'Calcular probabilidad'}
      </button>

      {analizar.isError && <ErrorCaja mensaje={analizar.error.message} onReintentar={() => analizar.mutate(texto)} />}

      {r && (
        <section className={s.resultado} aria-live="polite">
          {r.pctTotal !== null && (
            <div className={s.total}>
              <span className={s.totalPct} style={{ color: colorPct(r.pctTotal) }}>{r.pctTotal}%</span>
              <span className={s.totalTextos}>
                <b>Probabilidad de que salga todo</b>
                <span className={ui.sub}>Cuota justa ~{r.cuotaJusta?.toFixed(2)}. Si tu casa paga más, la apuesta tiene valor.</span>
              </span>
            </div>
          )}
          {r.consejo && <Aviso texto={r.consejo} />}
          {r.selecciones.length === 0 && !r.consejo && <div className={ui.vacio}>No reconocimos ningún partido.</div>}
          {r.selecciones.map((x, i) => (
            <div key={i} className={s.seleccion}>
              <button type="button" className={s.seleccionTextos} disabled={!x.partidoId}
                onClick={() => x.partidoId && navigate(`/partido/${x.partidoId}`)}>
                <span className={ui.sub} style={{ fontSize: 11 }}>
                  {x.partido}{x.fecha ? ` · ${etiquetaFecha(x.fecha, 'PROXIMO')}` : ''}
                </span>
                <span className={s.mercado}>{x.mercado ?? `"${x.leido}"`}</span>
                {x.nota && <span className={s.nota}>{x.nota}</span>}
              </button>
              {x.pct !== null && (
                <span className={s.derecha}>
                  <span className={s.pct} style={{ color: colorPct(x.pct) }}>{x.pct}%</span>
                  {x.cuota !== null && <span className={ui.sub} style={{ fontSize: 11 }}>~{x.cuota.toFixed(2)}</span>}
                </span>
              )}
            </div>
          ))}
          <p className={ui.sub} style={{ margin: 0, fontSize: 11 }}>
            Es una estimación con datos reales de los equipos. Ninguna apuesta es segura.
          </p>
        </section>
      )}
    </main>
  )
}
