// Iconos SVG de línea copiados del prototipo.
import type { ReactNode } from 'react'

export function IconoBalanza({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="var(--sobre-acento)" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 3v18" />
      <path d="M5 7h14" />
      <path d="M5 7l-3 6a3 3 0 0 0 6 0z" />
      <path d="M19 7l-3 6a3 3 0 0 0 6 0z" />
      <path d="M8 21h8" />
    </svg>
  )
}

export function IconoGoogle() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--texto)" strokeWidth="2"
      strokeLinecap="round" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 12h8" />
    </svg>
  )
}

export function IconoEstrella({ llena }: { llena: boolean }) {
  const color = llena ? 'var(--acento-texto)' : 'var(--texto-secundario)'
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill={llena ? color : 'none'} stroke={color} strokeWidth="2"
      strokeLinejoin="round" aria-hidden="true">
      <path d="M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3 6.4 20.2l1.1-6.2L3 9.6l6.2-.9z" />
    </svg>
  )
}

export function IconoBuscar() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--texto-secundario)" strokeWidth="2"
      strokeLinecap="round" aria-hidden="true">
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-3.5-3.5" />
    </svg>
  )
}

export function IconoVolver() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--texto)" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M15 18l-6-6 6-6" />
    </svg>
  )
}

export function IconoPartidos({ color }: { color: string }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7l4 3-1.5 4.5h-5L8 10z" />
    </svg>
  )
}

export function IconoPicks({ color }: { color: string }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <circle cx="12" cy="12" r="5" />
      <circle cx="12" cy="12" r="1" />
    </svg>
  )
}

export function IconoFavoritos({ color }: { color: string }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2"
      strokeLinejoin="round" aria-hidden="true">
      <path d="M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3 6.4 20.2l1.1-6.2L3 9.6l6.2-.9z" />
    </svg>
  )
}

export function IconoPerfil({ color }: { color: string }) {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" />
    </svg>
  )
}

function Trazo({ size = 18, color = 'currentColor', children }: { size?: number; color?: string; children: ReactNode }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {children}
    </svg>
  )
}

export function IconoEscudo({ size = 14 }: { size?: number }) {
  return (
    <Trazo size={size}>
      <path d="M12 3l8 3v6c0 4.5-3.4 8-8 9-4.6-1-8-4.5-8-9V6z" />
      <path d="M8.5 12l2.5 2.5 4.5-5" />
    </Trazo>
  )
}

export function IconoMas({ size = 16 }: { size?: number }) {
  return <Trazo size={size}><path d="M12 5v14M5 12h14" /></Trazo>
}

export function IconoCheck({ size = 16 }: { size?: number }) {
  return <Trazo size={size}><path d="M5 12.5l4.5 4.5L19 7.5" /></Trazo>
}

export function IconoCerrar({ size = 16 }: { size?: number }) {
  return <Trazo size={size}><path d="M6 6l12 12M18 6L6 18" /></Trazo>
}

export function IconoFlecha({ size = 18, arriba }: { size?: number; arriba: boolean }) {
  return <Trazo size={size}><path d={arriba ? 'M6 15l6-6 6 6' : 'M6 9l6 6 6-6'} /></Trazo>
}

export function IconoBoleto({ size = 18 }: { size?: number }) {
  return (
    <Trazo size={size}>
      <path d="M4 5h16v4a3 3 0 0 0 0 6v4H4v-4a3 3 0 0 0 0-6z" />
      <path d="M9 9h6M9 13h6" />
    </Trazo>
  )
}

export function IconoEnlace({ size = 16 }: { size?: number }) {
  return (
    <Trazo size={size}>
      <path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1" />
      <path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" />
    </Trazo>
  )
}

export function IconoImagen({ size = 16 }: { size?: number }) {
  return (
    <Trazo size={size}>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <circle cx="9" cy="10" r="1.6" />
      <path d="M21 16l-5-5-9 9" />
    </Trazo>
  )
}
