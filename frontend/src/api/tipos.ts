export type Modo = 'SEGURO' | 'EQUILIBRADO' | 'ARRIESGADO'
export type Tema = 'OSCURO' | 'CLARO' | 'AUTO'
export type Acento = 'LIMON' | 'VERDE' | 'CELESTE' | 'MORADO' | 'NARANJA'

export interface EstadisticasPicks {
  seguidos: number
  acertados: number
  porcentajeAcierto: number
}

export interface Usuario {
  id: number
  nombre: string
  email: string
  modo: Modo
  tipVisto: boolean
  ligasFavoritas: string[]
  estadisticas: EstadisticasPicks
  tema: Tema
  acento: Acento
}

export interface AuthRespuesta {
  token: string
  usuario: Usuario
}

export interface RegistroDatos {
  nombre: string
  email: string
  password: string
  modo: Modo
}

export interface LoginDatos {
  email: string
  password: string
}

// ---------- Partidos ----------

export type EstadoPartido = 'JUGADO' | 'HOY' | 'PROXIMO' | 'EN_VIVO' | 'SUSPENDIDO'
export type Pestana = 'JUGADO' | 'HOY' | 'PROXIMO'
export type Tono = 'VERDE' | 'ROJO' | 'MEDIO'

export interface EquipoRef {
  id: number
  nombre: string
  escudo: string | null
}

export interface Consejo {
  clave: string
  categoria: string
  texto: string
  pct: number
  confianza: string
  /** null si aún no se jugó o falta el dato para saberlo */
  acerto: boolean | null
  cuota: number
  blindado: boolean
}

export interface PartidoResumen {
  id: number
  fecha: string
  liga: { nombre: string; pais: string | null; logo: string | null }
  estadio: string | null
  estado: EstadoPartido
  local: EquipoRef
  visita: EquipoRef
  golesLocal: number | null
  golesVisita: number | null
  favorito: boolean
  consejo: Consejo | null
}

export interface ListaPartidos {
  partidos: PartidoResumen[]
  actualizadoEn: string | null
  aviso: string | null
  /** La búsqueda sigue en otra fuente: se vuelve a pedir en unos segundos */
  buscandoMas?: boolean
}

export interface Duelo {
  fecha: string
  estadio: string | null
  local: string
  visita: string
  golesLocal: number
  golesVisita: number
}

export interface H2h {
  hw: number
  d: number
  aw: number
  n: number
  goles: number
  btts: number
  over25: number
  lista: Duelo[]
}

/** Una de las 5 pruebas del pick blindado. paso = null si no hay datos (no cuenta). */
export interface Prueba {
  nombre: string
  detalle: string
  paso: boolean | null
}

export interface Mercado {
  clave: string
  texto: string
  /** certeza final (modelo + lo que pasó en los partidos reales) */
  pct: number
  tono: Tono
  salio: boolean | null
  cuota: number
  /** solo el modelo de goles esperados */
  modelo: number
  blindado: boolean
  pruebas: Prueba[]
}

export interface Categoria {
  clave: string
  nombre: string
  promedio: string
  mercados: Mercado[]
}

export interface PickBreve {
  clave: string
  texto: string
  pct: number
  categoria: string
  cuota: number
  blindado: boolean
}

export interface JugadorClave {
  num: string
  nombre: string
  equipo: string
  stat: string
  pct: number
  que: string
  tono: Tono
}

export interface Analisis {
  partido: PartidoResumen
  datosSuficientes: boolean
  mensaje: string | null
  rachaLocal: string
  rachaVisita: string
  ganador: { h: number; d: number; a: number; veredicto: string } | null
  consejo: Consejo | null
  porQue: string | null
  siguiendo: boolean
  tambien: PickBreve[]
  evita: PickBreve[]
  h2h: H2h | null
  categorias: Categoria[]
  resultadoFinal: {
    golesLocal: number
    golesVisita: number
    corners: number | null
    tarjetas: number | null
    rematesAPuerta: number | null
    resumen: string
  } | null
  caraACara: { etiqueta: string; local: number; visita: number }[]
  jugadores: JugadorClave[]
  aviso: string | null
}

export interface UltimoPartido {
  id: number
  fecha: string
  rival: string
  enCasa: boolean
  golesFavor: number
  golesContra: number
  resultado: 'G' | 'E' | 'P'
}

export interface Ultimos {
  equipo: string
  resumen: string
  partidos: UltimoPartido[]
}

// ---------- Picks ----------

export interface PickPartido {
  partidoId: number
  partido: string
  fecha: string
  clave: string
  texto: string
  pct: number
  categoria: string
  cuota: number
  blindado: boolean
}

export interface Combinada {
  nivel: 'MUY_SEGURA' | 'SEGURA' | 'RETO'
  nombre: string
  picks: PickPartido[]
  /** probabilidad de que salgan todos */
  pct: number
  cuota: number
}

export interface PicksDelDia {
  combinadas: Combinada[]
  mejores: PickPartido[]
}

export type EstadoPick = 'PENDIENTE' | 'ACERTADO' | 'FALLADO'

export interface PickSeguido {
  id: number
  partidoId: number
  partido: string
  fecha: string | null
  mercadoClave: string
  texto: string
  probabilidad: number
  cuota: number
  estado: EstadoPick
  creadoEn: string
}

export interface BoletoPick {
  partido: string
  fecha: string | null
  texto: string
  pct: number
  cuota: number
}

/** Boleto compartido por link (foto del momento en que se compartió). */
export interface Boleto {
  codigo: string
  autor: string
  creadoEn: string
  picks: BoletoPick[]
  pct: number
  cuota: number
}

export interface Conteo {
  picks: number
  acertados: number
  pct: number
}

/** Acierto real del modelo en partidos ya jugados (analizados con los datos de antes de cada partido). */
export interface Precision {
  calculadoEn: string
  partidos: number
  blindados: Conteo
  consejo: Conteo
}
