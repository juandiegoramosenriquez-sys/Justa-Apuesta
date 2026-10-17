import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

// Fuentes instaladas en el proyecto (no desde Google Fonts): así cargan también en el celular
import '@fontsource/tinos/400.css'
import '@fontsource/tinos/700.css'
import '@fontsource/tinos/400-italic.css'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/inter/700.css'
import './styles/global.css'

import { iniciarApariencia } from './lib/apariencia'
import App from './App'
import { SesionProvider } from './auth/SesionContext'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      // Un 401 o 404 no se arregla reintentando
      retry: (fallos, error) => fallos < 2 && !('status' in error && [401, 403, 404].includes(error.status as number)),
    },
  },
})

iniciarApariencia()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <SesionProvider>
          <App />
        </SesionProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
