# Justa Apuesta

App web *mobile-first* de **análisis de partidos de fútbol y probabilidades**. Muestra estadísticas, consejos y probabilidades con datos reales de API-Football. **No permite apostar.** Solo +18.

| Carpeta | Qué es |
|---|---|
| `backend/` | Spring Boot 4.1 (Java 21) · Spring Security + JWT · JPA + Flyway · MySQL |
| `frontend/` | React 19 + Vite + TypeScript · React Router 7 · TanStack Query |
| `docker-compose.yml` | MySQL 8 opcional en Docker (puerto 3307) |

## Estado de las fases

| Fase | Qué | Estado |
|---|---|---|
| 1 | Estructura, MySQL, migraciones Flyway, base de Spring Boot | ✅ |
| 2 | Registro, login, JWT + pantallas de login y registro | ✅ |
| 3 | Proveedor (API-Football) + sincronización + búsqueda bajo demanda | ✅ (falta probar con tu API key) |
| 4 | Motor de probabilidades en Java con tests + `/analisis` | ✅ |
| 5–6 | Lista con pestañas, buscador, tarjetas y detalle completo | ✅ |
| 7–8 | Picks, favoritos, perfil y resolución automática de picks | ✅ |
| 9 | Skeletons, errores amables, jalar para actualizar, barra inferior | ✅ |
| 10 | Subir a internet y APK | Pendiente (más adelante) |

---

## Correrlo en tu PC

**Requisitos:** Java 21 (IntelliJ ya lo trae), Node 20+, MySQL de XAMPP.

1. **API key:** abre `backend/.env` y pega tu clave después de `FOOTBALL_API_KEY=`. Ese archivo no se sube a GitHub.
2. **Base de datos:** enciende MySQL en XAMPP. La base `justa_apuesta` y sus tablas se crean solas.
   - XAMPP y el servicio de Windows `MySQL92` usan el puerto 3306: solo uno puede estar encendido.
3. **Backend:** en IntelliJ abre `backend/` y ejecuta `JustaApuestaApplication` (o doble clic en `backend/iniciar-backend.cmd`).
   - Comprueba: http://localhost:8080/api/health
4. **Frontend:** doble clic en `frontend/iniciar-frontend.cmd` (o `npm.cmd run dev` en `frontend/`) y abre http://localhost:5173

**Primera carga de datos:** busca un equipo (por ejemplo "Universitario"), o lanza la sincronización diaria a mano (`ADMIN_SYNC_KEY` también va en `backend/.env`):

```bash
curl -X POST http://localhost:8080/api/admin/sync -H "X-Admin-Key: TU_ADMIN_SYNC_KEY"
```

### Modo demo (sin gastar consultas)

Para revisar las pantallas con datos de prueba (equipos falsos de LaLiga), sin API key:

```bash
cd backend
mvnw spring-boot:test-run -Dspring-boot.run.main-class=pe.justaapuesta.DemoLocal
```

Luego busca "atletico" en la app. Ojo: los datos de prueba se guardan en tu MySQL.

### Tests

```bash
cd backend
mvnw test
```

Incluye el motor de probabilidades (`MotorTest`), el lector de API-Football (`ApiFootballMapperTest`), registro/login (`AuthIntegrationTest`) y el flujo completo con un proveedor falso (`FlujoCompletoTest`).

---

## Datos (API-Football)

- Todo pasa por el backend y se guarda en MySQL: el frontend nunca llama al proveedor.
- **Sincronización diaria** (4:30 a. m. hora de Lima) de las ligas de `application.yml` (`justa.sync.ligas`): partidos de hace 3 días a dentro de 7, sus estadísticas, el historial de equipos nuevos y limpieza de partidos de más de 2 años.
- **Cada 3 horas:** refresco de los partidos de hoy (1 consulta).
- **Búsqueda bajo demanda:** si un equipo no está guardado, se busca en el proveedor y se bajan sus últimos 10 partidos (con estadísticas y jugadores) y los próximos 5. Las búsquedas se reusan 7 días.
- **Límite del plan gratis:** 100 consultas al día y 10 por minuto. El backend espera si hace falta y deja de consultar cuando quedan menos de 5; entonces muestra la caché con el aviso "Datos actualizados hace X horas".
- Revisa en tu panel de API-Football qué temporadas incluye tu plan: si no incluye la actual, el proveedor responderá con un error de plan.

## Motor de probabilidades

`backend/src/main/java/pe/justaapuesta/analisis/Motor.java` copia exactamente las fórmulas del prototipo (`pois`, `over`, `formScore`, `adj`, `winProbs`, `markets`, `picksFor`, `lowPicks`, `conf`, `tone`, `reason`, `players`). Las entradas (`EntradasService`) salen de los **últimos 10 partidos reales** de cada equipo **anteriores al partido**; con menos de 3 se muestra "Datos insuficientes". Las categorías sin datos reales (por ejemplo, sin corners) no se muestran: nunca se inventan.

El análisis previo al partido se guarda en `analisis_cache`, así la probabilidad de un partido jugado es la que se calculó antes.
