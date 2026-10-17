import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { picksApi } from '../api/partidos'
import { useSesion } from '../auth/sesion'
import { useBoleto } from '../hooks/useBoleto'
import { etiquetaFecha } from '../lib/formato'
import { compartirImagen, imagenBoleto } from '../lib/imagenBoleto'
import { IconoBoleto, IconoCerrar, IconoEnlace, IconoFlecha, IconoImagen } from './iconos'
import s from './BoletoSheet.module.css'

const linkDe = (codigo: string) => `${window.location.origin}/b/${codigo}`

/** Mi boleto: barra fija abajo que sube con los picks guardados en la cuenta. */
export function BoletoSheet() {
  const b = useBoleto()
  const { usuario } = useSesion()
  const navigate = useNavigate()
  const [abierto, setAbierto] = useState(false)
  const [ocupado, setOcupado] = useState<'link' | 'imagen' | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)

  useEffect(() => {
    if (!mensaje) return
    const t = setTimeout(() => setMensaje(null), 2500)
    return () => clearTimeout(t)
  }, [mensaje])

  if (b.picks.length === 0) return null

  const compartirLink = async () => {
    setOcupado('link')
    try {
      const boleto = await picksApi.compartirBoleto()
      const url = linkDe(boleto.codigo)
      if (navigator.share) {
        try {
          await navigator.share({ title: 'Mi boleto · Justa Apuesta', url })
          return
        } catch (e) {
          if ((e as Error).name === 'AbortError') return
        }
      }
      await navigator.clipboard.writeText(url)
      setMensaje('Link copiado')
    } catch (e) {
      setMensaje((e as Error).message)
    } finally {
      setOcupado(null)
    }
  }

  const descargarImagen = async () => {
    setOcupado('imagen')
    try {
      const boleto = await picksApi.compartirBoleto()
      const blob = await imagenBoleto({ autor: usuario?.nombre ?? '', picks: boleto.picks, pct: boleto.pct,
        cuota: boleto.cuota, link: linkDe(boleto.codigo) })
      await compartirImagen(blob, `boleto-${boleto.codigo}.png`)
    } catch (e) {
      setMensaje((e as Error).message)
    } finally {
      setOcupado(null)
    }
  }

  const n = b.picks.length
  return (
    <>
      {abierto && <button type="button" className={s.fondo} aria-label="Cerrar boleto" onClick={() => setAbierto(false)} />}
      <section className={`${s.hoja} ${abierto ? s.abierta : ''}`} aria-label="Mi boleto">
        <button type="button" className={s.barra} onClick={() => setAbierto(!abierto)} aria-expanded={abierto}>
          <span className={s.asa} aria-hidden="true" />
          <span className={s.resumen}>
            <span className={s.icono}><IconoBoleto /></span>
            <span className={s.titulo}>Mi boleto</span>
            <span className={s.cantidad}>{n}</span>
          </span>
          <span className={s.totales}>
            <span className={s.pctTotal}>{b.pct}%</span>
            <span className={s.cuota}>~{b.cuota.toFixed(2)}</span>
            <IconoFlecha arriba={!abierto} />
          </span>
        </button>

        {abierto && (
          <div className={s.cuerpo}>
            <ul className={s.lista}>
              {b.picks.map((p) => (
                <li key={p.id} className={s.pick}>
                  <button type="button" className={s.pickTextos} onClick={() => { setAbierto(false); navigate(`/partido/${p.partidoId}`) }}>
                    <span className={s.pickTexto}>{p.texto}</span>
                    <span className={s.pickPartido}>{p.partido}{p.fecha ? ` · ${etiquetaFecha(p.fecha, 'PROXIMO')}` : ''}</span>
                  </button>
                  <span className={s.pickPct}>{p.probabilidad}%</span>
                  <span className={s.cuota}>~{p.cuota.toFixed(2)}</span>
                  <button type="button" className={s.quitar} aria-label={`Quitar ${p.texto}`}
                    onClick={() => b.alternar.mutate({ partidoId: p.partidoId, clave: p.mercadoClave })}>
                    <IconoCerrar size={14} />
                  </button>
                </li>
              ))}
            </ul>
            <div className={s.resumenFinal}>
              <div className={s.filaTotal}><span>Salen todos</span><span className={s.grande}>{b.pct}%</span></div>
              <div className={s.filaTotal}><span>Cuota aproximada</span><span className={s.grandeBlanco}>~{b.cuota.toFixed(2)}</span></div>
            </div>
            <div className={s.acciones}>
              <button type="button" className={s.principal} onClick={compartirLink} disabled={ocupado !== null}>
                <IconoEnlace /> {ocupado === 'link' ? 'Creando…' : 'Compartir link'}
              </button>
              <button type="button" className={s.secundario} onClick={descargarImagen} disabled={ocupado !== null}>
                <IconoImagen /> {ocupado === 'imagen' ? 'Creando…' : 'Imagen'}
              </button>
            </div>
            <button type="button" className={s.vaciar} onClick={() => b.vaciar.mutate()} disabled={b.vaciar.isPending}>
              Vaciar boleto
            </button>
            <p className={s.nota}>Cuotas aproximadas · Análisis, no apuestas · +18</p>
          </div>
        )}
        {mensaje && <div className={s.mensaje} role="status">{mensaje}</div>}
      </section>
    </>
  )
}
