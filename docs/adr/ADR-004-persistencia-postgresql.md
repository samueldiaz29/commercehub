# ADR-004: Persistencia Relacional con PostgreSQL, Compatibilidad Neon y Database-per-Service

## Estado
**Aprobado** `[DECISIÓN APROBADA]`

## Fecha
2026-10-22

## Contexto
Los dominios de negocio de CommerceHub (órdenes, pagos, catálogos e inventario con bloqueo transaccional) exigen:
- Cumplimiento riguroso de propiedades ACID a nivel de agregado local.
- Tipos de datos avanzados para montos monetarios de precisión exacta (`NUMERIC` / `BigDecimal`).
- Capacidad de almacenamiento semiestructurado para payloads de eventos y snapshots de estado de la Saga (`JSONB`).
- Compatibilidad para despliegues modernos serverless en la nube (Neon Database) y pruebas herméticas locales con contenedores.
- Gestión versionada y reproducible del esquema de base de datos a lo largo del tiempo.

## Decisión
1. **Motor de Base de Datos:** Adoptar **PostgreSQL** como motor relacional estándar para todos los microservicios, asegurando compatibilidad con **Neon** para entornos productivos/cloud.
2. **Patrón Database-per-Service:** Cada microservicio posee su propia base de datos lógica o esquema estrictamente aislado (`catalog_db`, `inventory_db`, `order_db`, `payment_db`). Queda prohibido el acceso directo o consultas cruzadas entre esquemas.
3. **Migraciones con Flyway:** El versionado de esquemas DDL y datos iniciales (seed data) se gestiona de forma automatizada mediante scripts de migración de **Flyway** en cada microservicio (`V1__...sql`).
4. **Pruebas de Integración:** Se utiliza **Testcontainers PostgreSQL** para validar repositorios y consultas JPA contra instancias reales de Postgres en JUnit 5.

## Consecuencias

### Positivas
- **Integridad y Exactitud Financiera:** Soporte robusto de transacciones ACID locales y tipos numéricos de coma fija, previniendo errores de redondeo en transacciones de compra.
- **Flexibilidad con JSONB:** Permite persistir el estado de la Saga y las cargas útiles de eventos en `order-service` sin requerir esquemas altamente fragmentados.
- **Compatibilidad Local y Cloud:** Mismo motor en Docker Compose local, Testcontainers en CI y Neon en producción gestionada.

### Negativas / Desafíos
- **Imposibilidad de JOINs Interservicios:** Consultas que requieren datos combinados (ej. detalle de orden con nombres de productos actualizados) deben resolverse mediante duplicación deliberada de datos en el agregado (snapshot) o composición en la capa de aplicación.
- **Mantenimiento de Múltiples Migraciones:** Cada servicio mantiene su propio directorio de migraciones Flyway independiente.

## Alternativas Consideradas y Descartadas
1. **Base de Datos NoSQL Documental (MongoDB):**
   - *Por qué se descartó:* Carece del rigor referencial y control de concurrencia pesimista/optimista requerido en los movimientos de inventario y conciliación de pagos.
2. **Base de Datos Relacional Única Compartida (Shared Database):**
   - *Por qué se descartó:* Viola el principio fundamental de autonomía de microservicios, crea un punto único de falla y permite que cambios de esquema en un dominio rompan consultas de otro servicio.
3. **Bases de Datos Embebidas en Memoria (H2):**
   - *Por qué se descartó:* Las diferencias en dialectos SQL, tipos de datos (falta de JSONB nativo de Postgres) y funciones entre H2 y PostgreSQL provocan que los tests pasen en local pero fallen en producción (*works on my machine*).
