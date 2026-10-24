# CommerceHub

**CommerceHub** es un proyecto de plataforma de comercio electrónico monocomercio (*single-merchant*). Su arquitectura objetivo se basa en microservicios desacoplados y patrones de consistencia eventual. Actualmente, `catalog-service` cuenta con su modelo de dominio y persistencia inicial; el resto de las funcionalidades de la plataforma aún está en desarrollo.

---

## 1. Propósito y Alcance del Sistema (Objetivos de Arquitectura)

Como objetivos funcionales previstos —aún no implementados—, CommerceHub permitirá:

**A los clientes:**
- Explorar el catálogo de productos y categorías con precios oficiales en soles peruanos (**PEN**).
- Gestionar un carrito de compras persistente.
- Ejecutar un proceso de **Checkout** coordinado mediante una **Saga Orquestada y Persistente**, con reserva temporal de stock (TTL de 15 minutos), tarifa plana de envío (**S/ 10.00**) y procesamiento de cobro simulado e idempotente con aislamiento de datos sensibles de tarjetas (sin almacenamiento de PAN ni CVV).
- Consultar el historial y estado logístico de sus pedidos, así como solicitar cancelaciones directas antes del empaque.

**A los administradores:**
- Administrar el catálogo de productos y categorías.
- Controlar existencias físicas y auditoría de inventario (Kardex).
- Gestionar las etapas de preparación, despacho y entrega de pedidos.
- Consultar métricas de ventas y débitos auditados por reembolsos confirmados.

---

## 2. Estructura del Monorepo

El repositorio contiene un monorepo modular. Los módulos backend tienen estructura Maven y clases de inicio; `catalog-service` además implementa el modelo y la persistencia inicial del catálogo. Las APIs del catálogo, el resto de funcionalidades de negocio y el frontend todavía no están implementados. Docker Compose proporciona PostgreSQL, RabbitMQ y Keycloak para desarrollo local.

```
commercehub/
├── docs/                     # Documentación técnica, funcional, ADRs y contratos
├── frontend/                 # Directorio base previsto para la SPA (React + TypeScript)
├── infrastructure/           # Configuración de infraestructura y despliegue
│   ├── docker/               # Docker Compose para infraestructura local
│   ├── monitoring/           # Directorio base previsto para observabilidad
│   └── terraform/            # Directorio base previsto para infraestructura como código
├── scripts/                  # Scripts de inicialización, seeds y utilidades
├── services/                 # Directorios base previstos para servicios backend (Java 25 / Spring Boot)
│   ├── api-gateway/          # Enrutamiento perimetral, CORS y validación JWT
│   ├── catalog-service/      # Catálogo de productos, categorías y precios
│   ├── inventory-service/    # Existencias, reservas temporales (15 min) y kardex
│   ├── notification-service/ # Consumidor asíncrono para notificaciones al cliente
│   ├── order-service/        # Carrito, pedidos y orquestación de la Saga
│   └── payment-service/      # Pagos simulados, idempotencia y reembolsos
├── .editorconfig             # Convenciones de edición y formato uniforme
├── .env.example              # Variables de entorno de referencia para desarrollo local
├── .gitignore                # Reglas estrictas de exclusión de artefactos y secretos
├── LICENSE                   # Licencia del proyecto
└── README.md                 # Descripción del proyecto e instrucciones iniciales (este archivo)
```

---

## 3. Stack Tecnológico Aprobado

> [!NOTE]
> Las siguientes tecnologías y herramientas corresponden a las **decisiones arquitectónicas aprobadas** (ver [ADRs](docs/adr/)). El backend está en desarrollo; el estado implementado del catálogo se limita a su modelo y persistencia inicial, sin endpoints REST.

| Componente | Tecnología o patrón aprobado | Alcance previsto |
| :--- | :--- | :--- |
| **Lenguaje base** | **Java 25 (LTS)** | Versión aprobada y configurada para compilar los servicios backend. |
| **Framework backend** | **Spring Boot** | Base prevista para los servicios backend. |
| **Borde / Perímetro** | **Spring Cloud Gateway** | Gateway reactivo y validación JWT como objetivos de arquitectura. |
| **Servicios de negocio** | **Spring MVC + Spring Data JPA** | Stack imperativo aprobado; el catálogo ya tiene su modelo persistente inicial. |
| **Persistencia** | **PostgreSQL / Neon** y patrón **Database-per-Service** | `catalog-service` persiste su modelo en su propia base PostgreSQL. |
| **Migración de esquemas** | **Flyway** | La migración inicial del catálogo está implementada; se versiona por servicio. |
| **Reducción de código Java** | **Lombok** | Dependencia y procesamiento de anotaciones configurados solo en `catalog-service`. |
| **Mensajería asíncrona** | **RabbitMQ**, Outbox y DLQ | Broker local disponible; la topología de negocio no está declarada. |
| **Gestión de identidad** | **Keycloak (OIDC / OAuth2)** | Servidor local disponible; los servicios aún no están integrados. |
| **Frontend** | **React + TypeScript** | SPA prevista; no implementada. |
| **Testing** | **JUnit 5, Mockito, Testcontainers** | Pruebas unitarias del dominio e integración PostgreSQL implementadas en `catalog-service`. |
| **Entorno local** | **Docker Compose** | PostgreSQL, RabbitMQ y Keycloak. |

---

## 4. Requisitos Previos del Entorno

Para compilar el backend se requiere JDK 25. Las pruebas de integración de `catalog-service` usan Testcontainers y requieren Docker. Maven se ejecuta mediante el wrapper incluido; Node.js se requiere al trabajar en el frontend y Docker Compose al levantar toda la infraestructura local.

1. **Java Development Kit (JDK) 25** (Versión LTS)
   - Verificar con: `java -version`
2. **Node.js (versión 20 LTS o superior)** y gestor de paquetes (`npm` o `pnpm`)
   - Verificar con: `node -v` y `npm -v`
3. **Docker y Docker Compose v2**
   - Verificar con: `docker --version` y `docker compose version`
4. **Git**
   - Verificar con: `git --version`

---

## 5. Instrucciones Iniciales de Desarrollo

### 5.1 Clonar el Repositorio

```bash
git clone https://github.com/samueldiaz29/commercehub.git
cd commercehub
```

### 5.2 Configurar las Variables de Entorno

El repositorio provee una plantilla con valores ficticios para desarrollo local:

```bash
cp .env.example .env
```

> [!CAUTION]
> NUNCA incorpores contraseñas productivas, llaves privadas ni credenciales reales en el archivo `.env`. El archivo `.env` se encuentra ignorado por Git.

### 5.3 Iniciar la infraestructura local

Crear `.env` desde la plantilla y levantar los servicios:

```bash
cp .env.example .env
docker compose -f infrastructure/docker/docker-compose.yml config
docker compose -f infrastructure/docker/docker-compose.yml up -d
docker compose -f infrastructure/docker/docker-compose.yml ps
```

PostgreSQL estará disponible en `localhost:5432`, RabbitMQ AMQP en `localhost:5672` y su consola en <http://localhost:15672>. Keycloak estará en <http://localhost:8180>. Las credenciales de desarrollo se leen desde `.env`; reemplázalas para cualquier entorno compartido.

Para detener los contenedores conservando los datos:

```bash
docker compose -f infrastructure/docker/docker-compose.yml down
```

Para borrar también los volúmenes persistentes (incluidas las bases locales), usa `docker compose -f infrastructure/docker/docker-compose.yml down -v`.

### 5.4 Estado del Proyecto: Funcionalidades Implementadas vs. Pendientes

Actualmente, lo **implementado** en el repositorio se limita a la **estructura base y documentación del monorepo**:
- Estructura modular de directorios para microservicios, frontend, infraestructura y scripts.
- Reglas uniformes de formato y edición ([`.editorconfig`](.editorconfig)).
- Exclusiones estrictas para evitar fugas de secretos y artefactos de compilación ([`.gitignore`](.gitignore)).
- Plantilla de variables de entorno de desarrollo con valores simulados seguros ([`.env.example`](.env.example)).
- Documentación canónica completa de arquitectura, contratos, reglas de negocio e historias de usuario ([`docs/`](docs/README.md)).

> [!IMPORTANT]
> **Componentes aún pendientes:**
> - **Microservicios backend (`services/*`):** `api-gateway`, `catalog-service`, `inventory-service`, `notification-service`, `order-service` y `payment-service` cuentan con esqueletos Maven y clases de inicio compilables. Las funcionalidades de negocio aún no están implementadas y se desarrollarán en las Issues dedicadas a cada dominio.
> - **Frontend (`frontend/`):** La aplicación cliente (React + TypeScript) **no está implementada** ni inicializada todavía.
> - **Integración de aplicaciones:** Los microservicios Java no se incluyen en el Compose y todavía no se conectan a la infraestructura local.

---

## 6. Convenciones y Documentación Canónica

Las decisiones técnicas y de negocio aprobadas están documentadas en [`docs/`](docs/README.md):

- **Guía de Gobernanza y Navegación:** [`docs/README.md`](docs/README.md)
- **Convenciones de Desarrollo y Código:** [`docs/conventions/convenciones-de-desarrollo.md`](docs/conventions/convenciones-de-desarrollo.md)
- **Arquitectura C4 y Límites de Contexto:** [`docs/architecture/arquitectura.md`](docs/architecture/arquitectura.md)
- **Saga de Checkout:** [`docs/architecture/saga-checkout.md`](docs/architecture/saga-checkout.md)
- **Contratos de API REST:** [`docs/api/contratos.md`](docs/api/contratos.md)
- **Eventos y Topología RabbitMQ:** [`docs/api/eventos.md`](docs/api/eventos.md)
- **Reglas de Negocio Transversales:** [`docs/business-rules/reglas-transversales.md`](docs/business-rules/reglas-transversales.md)
- **Máquinas de Estado y Transiciones:** [`docs/business-rules/estados-y-transiciones.md`](docs/business-rules/estados-y-transiciones.md)
- **Historias de Usuario y Criterios de Aceptación:** [`docs/requirements/historias-de-usuario.md`](docs/requirements/historias-de-usuario.md)
- **Decisiones Arquitectónicas (ADRs):** [`docs/adr/`](docs/adr/)

## 7. Compilación del backend

El backend se organiza como un proyecto Maven multi-módulo con Java 25 y Spring Boot 4. Cada servicio puede compilarse desde la raíz junto con sus verificaciones:

```bash
./mvnw verify
```

Para compilar un módulo específico y sus dependencias:

```bash
./mvnw -pl services/catalog-service -am verify
```

El wrapper descargará la distribución de Maven definida en `.mvn/wrapper/maven-wrapper.properties` si aún no está disponible localmente. No hace falta instalar Maven por separado.
