import type { Acento, Tema } from '../api/tipos'

const CLAVE = 'justa-apariencia'
const consulta = () => window.matchMedia('(prefers-color-scheme: light)')

let actual: { tema: Tema; acento: Acento } = { tema: 'OSCURO', acento: 'LIMON' }

/** Pone el tema y el color en <html> (data-modo y data-acento). AUTO sigue al celular o la PC. */
function pintar() {
  const claro = actual.tema === 'CLARO' || (actual.tema === 'AUTO' && consulta().matches)
  const raiz = document.documentElement
  raiz.dataset.modo = claro ? 'claro' : 'oscuro'
  raiz.dataset.acento = actual.acento.toLowerCase()
  // La barra del navegador del celular toma el color del fondo (cada acento tiene su negro o blanco teñido)
  const fondo = getComputedStyle(raiz).getPropertyValue('--fondo-app').trim()
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', fondo || (claro ? '#F7FBEF' : '#060905'))
}

/** Aplica y recuerda la apariencia en este navegador (para pintarla antes de cargar la cuenta). */
export function aplicarApariencia(tema: Tema, acento: Acento) {
  actual = { tema, acento }
  try {
    localStorage.setItem(CLAVE, JSON.stringify(actual))
  } catch {
    // sin almacenamiento: igual se aplica en esta visita
  }
  pintar()
}

/** Al abrir la web: la última apariencia usada en este navegador, y escucha si el celular cambia a claro/oscuro. */
export function iniciarApariencia() {
  try {
    const guardada = JSON.parse(localStorage.getItem(CLAVE) ?? 'null')
    if (guardada?.tema && guardada?.acento) actual = guardada
  } catch {
    // sin almacenamiento o dato roto: se queda el tema oscuro
  }
  pintar()
  consulta().addEventListener('change', () => actual.tema === 'AUTO' && pintar())
}
