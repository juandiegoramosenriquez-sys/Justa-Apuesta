/**
 * Lee el texto de una foto o captura del boleto, en el mismo celular (gratis, sin enviar la foto a nadie).
 * La librería se carga solo cuando se usa: la primera vez baja ~10 MB (idioma español e inglés) y queda guardada.
 */
export async function leerFoto(archivo: File, alAvanzar: (pct: number) => void): Promise<string> {
  const { createWorker } = await import('tesseract.js')
  const worker = await createWorker('spa+eng', 1, {
    logger: (m: { status: string; progress: number }) => {
      if (m.status === 'recognizing text') alAvanzar(Math.round(m.progress * 100))
    },
  })
  try {
    const { data } = await worker.recognize(await mejorar(archivo))
    return data.text
  } finally {
    await worker.terminate()
  }
}

/** Agranda las capturas chicas y las pasa a gris: así se leen mejor los nombres y las líneas. */
async function mejorar(archivo: File): Promise<HTMLCanvasElement | File> {
  try {
    const img = await createImageBitmap(archivo)
    const escala = img.width < 1200 ? 1200 / img.width : 1
    const lienzo = document.createElement('canvas')
    lienzo.width = Math.round(img.width * escala)
    lienzo.height = Math.round(img.height * escala)
    const ctx = lienzo.getContext('2d')
    if (!ctx) return archivo
    ctx.filter = 'grayscale(1) contrast(1.2)'
    ctx.drawImage(img, 0, 0, lienzo.width, lienzo.height)
    return lienzo
  } catch {
    return archivo
  }
}
