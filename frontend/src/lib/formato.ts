import type { EstadoPartido, Tono } from '../api/tipos'

const ZONA = 'America/Lima'

const hora = new Intl.DateTimeFormat('es-PE', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: ZONA })
const ymd = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: ZONA })
// Día de la semana, día y mes en hora de Lima (como números, para usar los nombres del prototipo)
const partes = new Intl.DateTimeFormat('en-US', { weekday: 'short', day: 'numeric', month: 'numeric', timeZone: ZONA })

// Mismos nombres que el prototipo (MONTHS): "sep", no "set"
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
const DIAS: Record<string, string> = { Sun: 'Dom', Mon: 'Lun', Tue: 'Mar', Wed: 'Mié', Thu: 'Jue', Fri: 'Vie', Sat: 'Sáb' }

function enLima(d: Date) {
  const p = Object.fromEntries(partes.formatToParts(d).map((x) => [x.type, x.value]))
  return { semana: DIAS[p.weekday] ?? p.weekday, dia: Number(p.day), mes: MESES[Number(p.month) - 1] }
}

/** "Jue 15 oct" en hora de Lima */
export function diaCorto(iso: string): string {
  const f = enLima(new Date(iso))
  return `${f.semana} ${f.dia} ${f.mes}`
}

/** Formato del prototipo: "Hoy · 20:00", "Jue 15 oct · 19:00", "Lun 12 oct · Final". */
export function etiquetaFecha(iso: string, estado: EstadoPartido): string {
  const d = new Date(iso)
  if (estado === 'JUGADO') return `${diaCorto(iso)} · Final`
  if (estado === 'EN_VIVO') {
    // Más de 2 h 30 min desde que empezó: ya terminó, falta que llegue el resultado
    const esperando = Date.now() - d.getTime() > 150 * 60_000
    return `${esHoy(d) ? 'Hoy' : diaCorto(iso)} · ${esperando ? 'Esperando resultado' : 'En vivo'}`
  }
  if (estado === 'SUSPENDIDO') return `${diaCorto(iso)} · Suspendido`
  return `${esHoy(d) ? 'Hoy' : diaCorto(iso)} · ${hora.format(d)}`
}

function esHoy(d: Date): boolean {
  return ymd.format(d) === ymd.format(new Date())
}

/** "12 oct" para la racha de un equipo */
export function diaMesCorto(iso: string): string {
  const f = enLima(new Date(iso))
  return `${f.dia} ${f.mes}`
}

export const COLOR = {
  verde: 'var(--verde)',
  rojo: 'var(--rojo)',
  medio: 'var(--lavanda)',
}

/** tone() del prototipo: >=65 verde, <=35 rojo, si no lavanda. */
export function colorTono(tono: Tono): string {
  return tono === 'VERDE' ? COLOR.verde : tono === 'ROJO' ? COLOR.rojo : COLOR.medio
}

export function tonoDe(pct: number): Tono {
  return pct >= 65 ? 'VERDE' : pct <= 35 ? 'ROJO' : 'MEDIO'
}

const PAISES: Record<string, string> = {
  England: 'Inglaterra', Spain: 'España', Italy: 'Italia', Germany: 'Alemania', France: 'Francia',
  Netherlands: 'Países Bajos', Portugal: 'Portugal', Brazil: 'Brasil', Argentina: 'Argentina', Peru: 'Perú',
  USA: 'Estados Unidos', 'United States': 'Estados Unidos', Mexico: 'México', Chile: 'Chile', Colombia: 'Colombia',
  Uruguay: 'Uruguay', Paraguay: 'Paraguay', Ecuador: 'Ecuador', Bolivia: 'Bolivia', Venezuela: 'Venezuela',
  Scotland: 'Escocia', Belgium: 'Bélgica', Turkey: 'Turquía', Greece: 'Grecia', Switzerland: 'Suiza',
  Austria: 'Austria', Denmark: 'Dinamarca', Sweden: 'Suecia', Norway: 'Noruega', Poland: 'Polonia',
  Croatia: 'Croacia', Serbia: 'Serbia', Russia: 'Rusia', Ukraine: 'Ucrania', 'Saudi-Arabia': 'Arabia Saudita',
  'Saudi Arabia': 'Arabia Saudita', Japan: 'Japón', 'South-Korea': 'Corea del Sur', China: 'China',
  Europe: 'Europa', 'South America': 'Sudamérica', Wales: 'Gales', Ireland: 'Irlanda', 'Czech-Republic': 'Chequia',
}

/** País en español; null si es una copa mundial ("World") o no hay país. */
export function paisEs(pais: string | null): string | null {
  if (!pais || pais === 'World' || pais === 'Worldwide') return null
  return PAISES[pais] ?? pais
}

/** Orden de las ligas en las listas: primero las más importantes (después de las favoritas del usuario). */
const PRIORIDAD_LIGAS = [
  'Champions League', 'Copa Libertadores', 'Premier League', 'LaLiga', 'Serie A', 'Bundesliga', 'Ligue 1',
  'Liga 1 Perú', 'Liga Profesional Argentina', 'MLS', 'Primeira Liga', 'Eredivisie', 'Campeonato Brasileiro Série A',
]

export function prioridadLiga(nombre: string, favoritas: Set<string>): number {
  if (favoritas.has(nombre)) return -1
  const i = PRIORIDAD_LIGAS.indexOf(nombre)
  return i < 0 ? PRIORIDAD_LIGAS.length : i
}

/** "hace 3 horas" */
export function haceCuanto(iso: string | null): string | null {
  if (!iso) return null
  const horas = Math.floor((Date.now() - new Date(iso).getTime()) / 3_600_000)
  if (horas < 1) return 'hace menos de 1 hora'
  return horas === 1 ? 'hace 1 hora' : `hace ${horas} horas`
}
