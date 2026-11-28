# ADR 0001: Migrar la base de datos a MySQL 8.4 LTS

## Estado

Aceptada.

## Contexto

El proyecto usaba PostgreSQL 16 y tiene como requisito usar MySQL. No existen datos que conservar.

## Decisión

Usar MySQL 8.4 LTS en desarrollo y en las pruebas, sin cambiar el contrato de la API ni el comportamiento observable.

- Esquema InnoDB con `utf8mb4` y la intercalación `utf8mb4_0900_as_ci`: no distingue mayúsculas y sí distingue acentos.
- Fechas en `DATETIME(6)` guardadas en UTC. La aplicación fuerza UTC en la sesión JDBC y en Hibernate.
- Aislamiento `READ COMMITTED`, configurado en el pool de conexiones.
- Las métricas suman microsegundos en la base y dividen en Java. No se usan las tablas de zonas horarias de MySQL.
- Las pruebas usan un contenedor `mysql:8.4` compartido. El usuario de la aplicación en las pruebas no es `root`; un script solo de pruebas le permite crear los esquemas temporales `flyway_*`.

### Excepción sobre las migraciones

Las migraciones `V1` a `V5` se reescribieron con los mismos nombres de archivo, tablas, columnas, restricciones e índices. Fue una excepción única para este cambio, posible porque no había datos. La regla de [CONTRIBUTING.md](../../CONTRIBUTING.md) sigue vigente: no se edita una migración ya aplicada, se crea una nueva.

## Consecuencias

### Diferencias de intercalación aceptadas

- En las restricciones de unicidad, algunos pares como `ß` y `ss`, o ciertas ligaduras, se consideran iguales.
- Al ordenar por `title` o `fullName`, MySQL toma en cuenta la puntuación y los espacios desde el primer nivel de comparación.
- Los valores que solo difieren en mayúsculas empatan al ordenar. Antes las minúsculas iban primero.

### Otras diferencias del motor

- Al comprobar una llave foránea, InnoDB bloquea en modo compartido la fila referenciada. Por eso, insertar una nota o una fila de historial puede esperar a que termine un movimiento o una calificación de la misma postulación.
- La espera por bloqueos termina tras `innodb_lock_wait_timeout` (50 s por defecto). Antes no tenía límite.
- Las sentencias DDL no son transaccionales: una migración que falle a la mitad puede dejar cambios parciales que hay que revisar a mano.
- InnoDB crea índices para las llaves foráneas que no tenían uno: `jobs.created_by`, `stage_history.moved_by` y `notes.author_id`.
- Para las claves únicas, MySQL informa el nombre como `tabla.restriccion`; para las llaves foráneas el nombre solo aparece en el mensaje de error. La traducción a respuestas 409 contempla ambos casos.

## Cómo recrear la base de datos local

Los datos de la base anterior no se migran.

1. En tu `.env`, define `DB_PORT=3306` y agrega `DB_ROOT_PASSWORD`. Compara con `.env.example`.
2. Retira el contenedor anterior y levanta MySQL:

   ```bash
   docker compose -f docker-compose.dev.yml down --remove-orphans
   docker compose -f docker-compose.dev.yml up -d
   ```

3. Arranca el backend; Flyway aplica `V1` a `V5`.

El volumen de la base anterior no se borra; puedes eliminarlo con `docker volume rm` cuando ya no lo necesites.

Para empezar de cero con MySQL, `docker compose -f docker-compose.dev.yml down -v` borra el volumen `ats_mysqldata` y todos sus datos. Las contraseñas del `.env` solo se aplican cuando el volumen se crea.
