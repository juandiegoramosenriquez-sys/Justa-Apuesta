import { IconoBalanza } from './iconos'
import s from './Logo.module.css'

type Tamano = 'grande' | 'cabecera'

export function LogoIcono({ tamano }: { tamano: Tamano }) {
  const grande = tamano === 'grande'
  return (
    <div className={`${s.icono} ${grande ? s.iconoGrande : s.iconoCabecera}`}>
      <IconoBalanza size={grande ? 40 : 24} />
    </div>
  )
}

export function LogoTexto({ tamano }: { tamano: Tamano }) {
  return (
    <div className={`${s.texto} ${tamano === 'grande' ? s.textoGrande : s.textoCabecera}`}>
      Justa <span className={s.verde}>Apuesta</span>
    </div>
  )
}
