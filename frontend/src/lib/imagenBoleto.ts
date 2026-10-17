import type { BoletoPick } from '../api/tipos'
import { etiquetaFecha } from './formato'

interface DatosImagen {
  autor: string
  picks: BoletoPick[]
  pct: number
  cuota: number
  link: string | null
}

const ANCHO = 1080
const MARGEN = 72
const ALTO_PICK = 150

/** Recorta el texto con "…" para que quepa en el ancho. */
function ajustar(ctx: CanvasRenderingContext2D, texto: string, ancho: number) {
  if (ctx.measureText(texto).width <= ancho) return texto
  let t = texto
  while (t.length > 1 && ctx.measureText(t + '…').width > ancho) t = t.slice(0, -1)
  return t + '…'
}

function lineaPunteada(ctx: CanvasRenderingContext2D, y: number) {
  ctx.save()
  ctx.strokeStyle = '#34461B'
  ctx.lineWidth = 3
  ctx.setLineDash([14, 12])
  ctx.beginPath()
  ctx.moveTo(MARGEN, y)
  ctx.lineTo(ANCHO - MARGEN, y)
  ctx.stroke()
  ctx.restore()
}

/** Dibuja el boleto como imagen PNG (1080 de ancho), con los colores de la app. */
export async function imagenBoleto(d: DatosImagen): Promise<Blob> {
  // El canvas solo usa fuentes ya cargadas: se piden antes de dibujar
  await Promise.all(['700 64px "Tinos"', '400 30px "Tinos"']
    .map((f) => document.fonts.load(f).catch(() => null)))
  const alto = 330 + d.picks.length * ALTO_PICK + 260 + (d.link ? 120 : 0)
  const canvas = document.createElement('canvas')
  canvas.width = ANCHO
  canvas.height = alto
  const ctx = canvas.getContext('2d')!
  const titulo = (px: number) => `700 ${px}px "Tinos", "Times New Roman", serif`
  const cuerpo = (peso: number, px: number) => `${peso >= 600 ? 700 : 400} ${px}px "Tinos", "Times New Roman", serif`

  ctx.fillStyle = '#070908'
  ctx.fillRect(0, 0, ANCHO, alto)
  ctx.fillStyle = '#C6F432'
  ctx.fillRect(0, 0, ANCHO, 14)

  let y = 120
  ctx.textBaseline = 'alphabetic'
  ctx.fillStyle = '#DBFB7E'
  ctx.font = titulo(64)
  ctx.fillText('JUSTA APUESTA', MARGEN, y)
  ctx.fillStyle = '#9EB0A6'
  ctx.font = cuerpo(500, 30)
  ctx.textAlign = 'right'
  ctx.fillText(new Date().toLocaleDateString('es-PE', { day: 'numeric', month: 'short', year: 'numeric' }), ANCHO - MARGEN, y)
  ctx.textAlign = 'left'
  y += 60
  ctx.fillText(`${d.autor ? `Boleto de ${d.autor.split(' ')[0]} · ` : ''}${d.picks.length} ${d.picks.length === 1 ? 'pick' : 'picks'}`, MARGEN, y)
  y += 50
  lineaPunteada(ctx, y)
  y += 30

  for (const p of d.picks) {
    y += 60
    ctx.fillStyle = '#F5F8F6'
    ctx.font = cuerpo(700, 40)
    ctx.fillText(ajustar(ctx, p.texto, ANCHO - MARGEN * 2 - 240), MARGEN, y)
    ctx.textAlign = 'right'
    ctx.fillStyle = '#2EE58A'
    ctx.font = titulo(52)
    ctx.fillText(`${p.pct}%`, ANCHO - MARGEN, y)
    ctx.textAlign = 'left'
    y += 48
    ctx.fillStyle = '#9EB0A6'
    ctx.font = cuerpo(500, 30)
    const fecha = p.fecha ? ` · ${etiquetaFecha(p.fecha, 'PROXIMO')}` : ''
    ctx.fillText(ajustar(ctx, p.partido + fecha, ANCHO - MARGEN * 2 - 240), MARGEN, y)
    ctx.textAlign = 'right'
    ctx.fillText(`cuota ~${p.cuota.toFixed(2)}`, ANCHO - MARGEN, y)
    ctx.textAlign = 'left'
    y += 42
  }

  y += 10
  lineaPunteada(ctx, y)
  y += 80
  ctx.fillStyle = '#9EB0A6'
  ctx.font = cuerpo(500, 34)
  ctx.fillText('Probabilidad total', MARGEN, y)
  ctx.textAlign = 'right'
  ctx.fillStyle = '#DBFB7E'
  ctx.font = titulo(64)
  ctx.fillText(`${d.pct}%`, ANCHO - MARGEN, y)
  ctx.textAlign = 'left'
  y += 76
  ctx.fillStyle = '#9EB0A6'
  ctx.font = cuerpo(500, 34)
  ctx.fillText('Cuota aproximada', MARGEN, y)
  ctx.textAlign = 'right'
  ctx.fillStyle = '#F5F8F6'
  ctx.font = titulo(64)
  ctx.fillText(`~${d.cuota.toFixed(2)}`, ANCHO - MARGEN, y)
  ctx.textAlign = 'left'

  if (d.link) {
    y += 60
    ctx.fillStyle = '#101512'
    ctx.beginPath()
    ctx.roundRect(MARGEN, y, ANCHO - MARGEN * 2, 90, 20)
    ctx.fill()
    ctx.fillStyle = '#DBFB7E'
    ctx.font = cuerpo(700, 30)
    ctx.fillText(ajustar(ctx, d.link.replace(/^https?:\/\//, ''), ANCHO - MARGEN * 2 - 60), MARGEN + 30, y + 56)
    y += 90
  }

  ctx.fillStyle = '#71857A'
  ctx.font = cuerpo(500, 26)
  ctx.textAlign = 'center'
  ctx.fillText('Análisis con datos reales · No es una apuesta · +18', ANCHO / 2, alto - 50)

  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('No se pudo crear la imagen.'))), 'image/png'))
}

/** Comparte la imagen si el celular lo permite; si no, la descarga. */
export async function compartirImagen(blob: Blob, nombre: string) {
  const archivo = new File([blob], nombre, { type: 'image/png' })
  if (navigator.canShare?.({ files: [archivo] })) {
    try {
      await navigator.share({ files: [archivo], title: 'Mi boleto · Justa Apuesta' })
      return
    } catch (e) {
      if ((e as Error).name === 'AbortError') return
    }
  }
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nombre
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}
