# Convenciones de Desarrollo y Organización del Código — CommerceHub

Este documento establece los estándares de ingeniería, nomenclatura, arquitectura interna y buenas prácticas que rigen la construcción del monorepo **CommerceHub**.

Su cumplimiento es obligatorio para todo desarrollador y agente de inteligencia artificial involucrado en el proyecto.

---

## 1. Organización del Monorepo

CommerceHub está estructurado como un monorepo multimodular organizado por dominios desacoplados:

```
commercehub/
├── docs/                     # Documentación canónica (Arquitectura, ADRs, contratos, reglas)
│   ├── adr/                  # Architectural Decision Records formales
│   ├── api/                  # Especificación de contratos REST y topología de eventos RabbitMQ
│   ├── architecture/         # Diagramas C4, diseño de límites y flujo de Sagas
│   ├── business-rules/       # Reglas de negocio globales, políticas de estado y cobro
│   ├── conventions/          # Estándares de desarrollo y directrices de ingeniería (este archivo)
│   └── requirements/         # Historias de usuario, épicas y criterios de aceptación
├── frontend/                 # Aplicación SPA desacoplada (React + TypeScript)
├── infrastructure/           # Configuración de entornos, orquestación y despliegue
│   ├── docker/               # Directorio previsto para manifiestos de Docker Compose
│   ├── monitoring/           # Configuraciones de observabilidad y métricas
│   └── terraform/            # Infraestructura como Código (IaC) para provisión cloud
├── scripts/                  # Scripts de inicialización, seeds y soporte al desarrollo
├── services/                 # Microservicios backend independientes (Java 25 / Spring Boot)
│   ├── api-gateway/          # Perímetro reactivo, enrutamiento y validación JWT
│   ├── catalog-service/      # Catálogo de productos y precios oficiales en PEN
│   ├── inventory-service/    # Existencias físicas, reservas temporales (15 min) y kardex
│   ├── notification-service/ # Consumidor asíncrono para envío simulado de notificaciones
│   ├── order-service/        # Carrito, pedidos y orquestador persistente de la Saga
│   └── payment-service/      # Pasarela simulada, idempotencia y reembolsos
├── .editorconfig             # Unificación de reglas de formateo y edición
├── .env.example              # Plantilla de variables de entorno seguras para desarrollo local
├── .gitignore                # Reglas estrictas de exclusión de artefactos y secretos
├── LICENSE                   # Licencia del proyecto
└── README.md                 # Guía general de inicio y bienvenida al proyecto
```

---

## 2. Arquitectura Interna de los Microservicios (Java 25)

Cada microservicio en `services/` mantiene autonomía funcional y toma como base una arquitectura en capas limpia orientada al dominio (*Clean / Layered Architecture*).

> [!NOTE]
> **Guía de Referencia y Responsabilidad por Servicio:**
> Las capas descritas a continuación constituyen una **guía arquitectónica de referencia**. Su implementación no debe aplicarse de forma rígida o universal a todos los módulos, sino que debe **adaptarse a la responsabilidad específica y al paradigma de cada servicio** según los ADR aprobados:
> - **Servicios de Negocio Transaccionales (`order-service`, `catalog-service`, `inventory-service`, `payment-service`):** Implementan el modelo completo de capas con Spring MVC y Spring Data JPA ([ADR-004](file:///home/samuel/01_Proyectos/commercehub/docs/adr/ADR-004-persistencia-postgresql.md), [ADR-005](file:///home/samuel/01_Proyectos/commercehub/docs/adr/ADR-005-spring-mvc.md)).
> - **API Gateway (`api-gateway`):** Opera como perímetro reactivo basado en Spring Cloud Gateway y WebFlux ([ADR-005](file:///home/samuel/01_Proyectos/commercehub/docs/adr/ADR-005-spring-mvc.md)). No utiliza controladores Spring MVC tradicionales, agregados de dominio ni repositorios JPA, estructurándose en torno a filtros perimetrales, rutas y configuración de seguridad.
> - **Consumidores Asíncronos (`notification-service`):** Centrado en la recepción y procesamiento de eventos AMQP ([ADR-001](file:///home/samuel/01_Proyectos/commercehub/docs/adr/ADR-001-arquitectura-microservicios.md), [ADR-003](file:///home/samuel/01_Proyectos/commercehub/docs/adr/ADR-003-mensajeria-rabbitmq.md)); no requiere forzar adaptadores REST (`controller`) o persistencia relacional si su caso de uso no los demanda.

- **Paquete raíz base:** `com.commercehub.<servicio>` (ej. `com.commercehub.order`, `com.commercehub.catalog`).

### Capas de Referencia y Responsabilidades:
1. **`controller` / `api`:**
   - Adaptadores de entrada REST (para servicios que exponen endpoints HTTP sincrónicos).
   - Manejo de solicitudes HTTP, validación declarativa con Jakarta Validation (`@Valid`, `@NotNull`, etc.).
   - Mapeo bidireccional entre DTOs y modelos de aplicación/dominio.
   - Manejo centralizado de excepciones mediante `@RestControllerAdvice` retornando RFC 7807 Problem Details.
2. **`service` / `application`:**
   - Casos de uso y lógica de coordinación de negocio.
   - Demarcación transaccional declarativa (`@Transactional`).
   - Orquestación de llamadas a repositorios, clientes y publicadores de eventos.
3. **`domain`:**
   - Entidades JPA y agregados de persistencia (donde aplique persistencia relacional).
   - Enums de estados y transiciones (`OrderStatus`, `PaymentStatus`, etc.).
   - Objetos de valor (*Value Objects*).
   - Excepciones de dominio específicas (sin acoplamiento a HTTP).
4. **`repository` / `infrastructure.persistence`:**
   - Interfaces `JpaRepository` de Spring Data JPA (en servicios con base de datos propia, [ADR-004](file:///home/samuel/01_Proyectos/commercehub/docs/adr/ADR-004-persistencia-postgresql.md)).
   - Consultas personalizadas JPQL / Criteria API.
   - Scripts de migración Flyway ubicados en `src/main/resources/db/migration/V{n}__{descripcion}.sql`.
5. **`messaging` / `infrastructure.messaging`:**
   - Publicador de eventos hacia la tabla de **Transactional Outbox** ([ADR-006](file:///home/samuel/01_Proyectos/commercehub/docs/adr/ADR-006-saga-orquestada.md)).
   - Consumidores `@RabbitListener` con soporte de deserialización JSON y control de idempotencia previa.
6. **`client` / `infrastructure.client`:**
   - Adaptadores para consumo de APIs REST de otros servicios o proveedores externos.
7. **`config`:**
   - Clases de configuración de Spring Boot (`@Configuration`), beans de RabbitMQ, seguridad y configuraciones técnicas específicas según las necesidades del módulo.

---

## 3. Convenciones de Nomenclatura (Naming Conventions)

### 3.1 Java
- **Paquetes:** Todo en minúsculas continuas, sin guiones ni caracteres especiales (`com.commercehub.inventory.domain`).
- **Clases y Enums:** `PascalCase` sustantivo (`ProductService`, `OrderStatus`, `ReservationEntity`).
- **Interfaces:** `PascalCase` sin prefijo `I` (`PaymentProviderGateway`, `OrderRepository`).
- **Métodos y Variables:** `camelCase` verbo o sustantivo (`calculateTotal()`, `idempotencyKey`).
- **Constantes:** `UPPER_SNAKE_CASE` (`DEFAULT_SHIPPING_FEE`, `MAX_RETRY_COUNT`).
- **DTOs de Petición/Respuesta:** `PascalCase` con sufijo descriptivo (`CreateProductRequest`, `ProductSummaryResponse`).
- **Eventos de Dominio:** Sustantivo en participio/tiempo pasado que exprese el hecho ocurrido (`OrderConfirmedEvent`, `InventoryReservationExpiredEvent`).
- **Excepciones:** `PascalCase` con sufijo `Exception` (`ResourceNotFoundException`, `InsufficientStockException`).
- **Tests:** Clase bajo prueba + `Test` para unitarios (`OrderServiceTest.java`) o + `IT` para integración (`OrderRepositoryIT.java`).

### 3.2 Base de Datos (PostgreSQL)
- **Nombres de Tablas:** Minúsculas en `snake_case`, preferentemente en plural (`orders`, `order_items`, `products`, `saga_instances`, `outbox_events`).
- **Columnas:** Minúsculas en `snake_case` (`id`, `order_id`, `unit_price`, `idempotency_key`, `created_at`).
- **Claves Primarias:** `pk_<nombre_tabla>` (ej. `pk_orders`).
- **Claves Foráneas:** `fk_<tabla_origen>_<tabla_destino>` (ej. `fk_order_items_orders`).
- **Índices:** `idx_<nombre_tabla>_<nombre_columna>` (ej. `idx_products_sku`, `idx_orders_customer_id`).
- **Restricciones de Unicidad:** `uk_<nombre_tabla>_<columna>` (ej. `uk_products_sku`).
- **Archivos de Migración Flyway:** `V<Numero>__<descripcion_snake_case>.sql` (ej. `V1__init_catalog_schema.sql`, `V2__add_product_indexes.sql`).

### 3.3 API REST
- **Rutas de Recursos:** En minúsculas y plural, utilizando `kebab-case` para nombres compuestos (`/api/v1/products`, `/api/v1/order-items`).
- **Identificadores de Ruta:** En `camelCase` dentro del path (`/api/v1/orders/{orderId}`).
- **Parámetros de Consulta (Query Params):** En `snake_case` (`/api/v1/products?category_id=cat-01&page=0&size=20`).
- **Encabezados HTTP:** Notación estándar `Train-Case` (`Authorization`, `Idempotency-Key`, `X-Correlation-Id`).
- **Respuestas de Error:** Cumplimiento del estándar RFC 7807 / RFC 9457 (*Problem Details for HTTP APIs*).

### 3.4 Mensajería Asíncrona (RabbitMQ)
- **Exchanges:** Minúsculas en `kebab-case` jerárquico (`commercehub.events`, `commercehub.dlx`).
- **Routing Keys:** Separadas por puntos bajo el patrón `<dominio>.<evento-en-pasado>` (ej. `order.created`, `order.confirmed`, `payment.authorized`, `inventory.reservation-expired`).
- **Colas (Queues):** Identificadas por servicio consumidor y propósito: `<servicio>.<dominio>.<tipo>.queue` (ej. `notif.order-events.queue`, `order.compensation.queue`, `commercehub.dead-letter.queue`).

### 3.5 Frontend (React + TypeScript)
- **Componentes:** `PascalCase` para el archivo y la función/constante exportada (`ProductList.tsx`, `CartSummary.tsx`).
- **Hooks Personalizados:** Prefijo `use` en `camelCase` (`useCart.ts`, `useAuth.ts`).
- **Servicios y Clientes API:** `camelCase` con sufijo `Api` o `Service` (`productApi.ts`, `storageService.ts`).
- **Interfaces y Tipos:** `PascalCase` sin prefijo `I` (`Product`, `CartItem`, `CheckoutFormData`).
- **Archivos de Estilos:** Co-ubicados o en carpeta designada (`ProductCard.module.css`).

---

## 4. Convenciones de Control de Versiones (Git)

### 4.1 Estrategia de Ramas
- `main`: Código estable listo para despliegue y validación final.
- `feature/<nombre>`: Desarrollo de nuevas funcionalidades o épicas específicas (ej. `feature/samuel`, `feature/saga-checkout`).
- `fix/<nombre>`: Correcciones de defectos o bugs identificados.
- `docs/<nombre>`: Actualizaciones exclusivas de documentación técnica o funcional.
- `infra/<nombre>`: Ajustes de infraestructura, Docker Compose, CI/CD o configuración base.

### 4.2 Formato de Commits (Conventional Commits)
Los mensajes de confirmación deben seguir el formato:
`<tipo>(<ámbito opcional>): <descripción concisa en imperativo y minúsculas>`

Tipos permitidos:
- `feat`: Nueva funcionalidad para el usuario o servicio.
- `fix`: Corrección de un fallo o error.
- `docs`: Modificaciones exclusivas en documentación.
- `refactor`: Refactorización de código sin alterar comportamiento externo.
- `test`: Incorporación o mejora de pruebas unitarias o de integración.
- `infra`: Modificaciones en Docker, scripts, dependencias base o configuración de entorno.
- `chore`: Tareas rutinarias de mantenimiento o configuración de herramientas.

Ejemplos:
- `infra: setup base monorepo structure and editorconfig`
- `docs(adr): document saga orchestration decision`
- `feat(catalog): implement public product listing endpoint`

---

## 5. Convenciones de Edición y Exclusión de Archivos

- **Formateo Automático:** Todo archivo debe ajustarse a las especificaciones de `.editorconfig`:
  - Codificación de caracteres: `UTF-8`.
  - Finales de línea: `LF` (Unix/Linux).
  - Sangría: **4 espacios** para código Java y XML; **2 espacios** para TypeScript, JavaScript, JSON, YAML y Markdown.
  - Inserción obligatoria de salto de línea final (*final newline*).
  - Eliminación automática de espacios en blanco al final de línea (*trim trailing whitespace*).
- **Exclusión en Git:** Ningún archivo ignorado por `.gitignore` debe ser forzado al repositorio (`git add -f` está terminantemente prohibido). En particular:
  - Archivos de entorno con datos locales (`.env`).
  - Directorios de compilación (`target/`, `build/`, `dist/`).
  - Dependencias descargadas (`node_modules/`).
  - Archivos de configuración privada de IDE (`.idea/`, `.vscode/*` locales).
  - Certificados, llaves privadas o credenciales sensibles (`*.pem`, `*.key`, `secrets/`).

---

## 6. Convenciones de Testing y Calidad

- **Versión de Lenguaje:** **Java 25**. No se permite degradar la versión del compilador ni el bytecode a versiones previas.
- **Pruebas Unitarias:** Construidas con **JUnit 5** y **Mockito**. Se ejecutan en memoria de forma aislada, rápida y determinista.
- **Pruebas de Integración:** Emplean **Testcontainers** para levantar contenedores efímeros reales de PostgreSQL y RabbitMQ, asegurando que las migraciones Flyway y los contratos de mensajería se ejecuten de forma fidedigna.
- **Concurrencia en Servicios Spring MVC (Virtual Threads):** Conforme a lo aprobado en el [ADR-005](file:///home/samuel/01_Proyectos/commercehub/docs/adr/ADR-005-spring-mvc.md), los microservicios de negocio imperativos basados en Spring MVC (`catalog-service`, `inventory-service`, `order-service` y `payment-service`) deben aprovechar el soporte nativo de Virtual Threads de Java 25 configurando `spring.threads.virtual.enabled=true`. Esta directriz aplica a los servicios de negocio indicados en el ADR y no se impone a componentes reactivos como `api-gateway` (basado en Spring Cloud Gateway / Netty) ni a otros módulos donde no esté contemplada en las decisiones arquitectónicas.
- **Idempotencia:** Toda operación de cobro o mutación de orden debe ser verificada bajo escenarios de duplicación de peticiones utilizando la clave `Idempotency-Key`.
