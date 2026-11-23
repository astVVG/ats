# Guía de contribución

Este documento describe las convenciones de trabajo de Mini ATS. Todo cambio debe seguirlas.

## 1. Ramas permanentes

Todas las ramas permanentes nacen de `main`:

| Rama      | Propósito                                                                                                   |
| --------- | ----------------------------------------------------------------------------------------------------------- |
| `main`    | Base fija. Después del commit inicial no se hace commit, merge ni push sobre ella.                          |
| `develop` | Integración. Es la rama por defecto y el destino de las ramas de trabajo.                                   |
| `test`    | Validación. Jenkins construye y prueba desde aquí. Solo recibe PRs desde `develop`.                         |
| `prod`    | Producción. Solo recibe PRs desde `test` al cerrar un release.                                              |

Flujo: rama de trabajo → `develop` → `test` → `prod`.

Nada se hace directo sobre las ramas permanentes.

## 2. Ramas de trabajo

Las ramas de trabajo salen de `develop`, con estos prefijos:

- `feature/<alcance>-<descripción>`
- `fix/...`
- `chore/...`
- `docs/...`
- `refactor/...`
- `tests/...`

Única excepción: `hotfix/...`, que sale de `prod`.

No se usa el prefijo `test/` porque choca con la rama `test`.

Alcances: `api`, `web`, `db`, `infra`.

Ejemplos: `feature/api-auth-jwt`, `feature/web-kanban-board`.

Las ramas de trabajo son de vida corta (1 a 3 días) y se actualizan con `develop` por rebase.

## 3. Commits

Se usa [Conventional Commits](https://www.conventionalcommits.org/) con el formato:

```text
tipo(alcance): descripción en imperativo
```

Tipos: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`, `perf`, `build`, `ci`.

Ejemplo: `feat(api): add JWT authentication`.

## 4. Pull requests

- Pequeños (idealmente menos de 400 líneas).
- Título en formato Conventional Commit.
- Se usa la plantilla de PR ([.github/pull_request_template.md](.github/pull_request_template.md)).

Estrategia de merge:

| Origen → destino              | Estrategia                  |
| ----------------------------- | --------------------------- |
| Rama de trabajo → `develop`   | Squash merge                |
| `develop` → `test`            | Merge commit (sin squash)   |
| `test` → `prod`               | Merge commit (sin squash)   |

Hotfix: sale de `prod`, entra a `prod` por PR y después se lleva a `develop`.

## 5. Cierre de etapa

Para cerrar una etapa se requiere:

1. PR de `develop` a `test`.
2. Jenkins en verde.
3. Tag en `test`: `v0.1.0`, `v0.2.0`, `v0.3.0`, `v0.4.0`, `v0.5.0` y `v0.9.0`.

El release `v1.0.0` se etiqueta en `prod`.

Jenkins construye y prueba solo la rama `test`; el deploy sale de `prod` (Etapa 6).

El detalle de etapas y tags está en [docs/stages.md](docs/stages.md).

## 6. Definition of Done por PR

- Compila y pasan todas las pruebas.
- Endpoint nuevo con pruebas y documentado en OpenAPI.
- Pantalla nueva con estados de carga, error y vacío.
- Lint y formato limpios (`mvn spotless:check`, `npm run lint`).
- Sin secretos en el repo.

## 7. Reglas de backend

- Controllers delgados; la lógica vive en los services.
- Solo DTOs (records); nunca se exponen entidades.
- Errores con `@RestControllerAdvice` y `ProblemDetail`.
- Flyway manda el esquema (`ddl-auto=validate`). Nunca se edita una migración ya aplicada; se crea una nueva (`V3__...`).
- Perfiles: `dev` (por defecto), `test` y `prod`.
- Secretos solo por variables de entorno.

## 8. Reglas de frontend

- TypeScript `strict` y sin `any` sin justificación.
- Los tipos de la API se generan con `openapi-typescript` (`npm run api:types`), no a mano.
- Toda llamada a la API pasa por hooks de TanStack Query.
- Toda pantalla maneja los estados de carga, error y vacío.

## 9. Comandos de verificación

| Ámbito        | Comando                                         |
| ------------- | ----------------------------------------------- |
| Backend       | `./mvnw -B verify`                              |
| Frontend      | `npm run lint && npm run test && npm run build` |
| Tipos de API  | `npm run api:types`                             |

> Estos comandos estarán disponibles conforme se completen las tareas 0.2 y 0.5.

## 10. Estructura del monorepo

```text
ats/
  backend/
  frontend/
  docs/
    adr/
  infra/
```

### Backend

Organización por feature. Paquete base de Java: **por definir**.

Features: `common`, `security`, `user`, `job`, `candidate`, `application`, `note`, `metrics`.

Cada feature contiene: `controller`, `service`, `repository`, `domain` y `dto`.

### Frontend

```text
src/
  app/
  features/
    auth/
    jobs/
    candidates/
    board/
    metrics/
  shared/
```

## 11. Metodología de trabajo

Una tarea es una rama y un PR pequeño.
