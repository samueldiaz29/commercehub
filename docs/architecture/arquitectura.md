# Arquitectura del Sistema — CommerceHub

Este documento describe la arquitectura global de **CommerceHub**, sus límites de contexto (Bounded Contexts), responsabilidades por componente, patrones de persistencia, integración asíncrona y seguridad.

---

## 1. Visión General del Sistema y Diagrama C4 (Contenedores)

CommerceHub implementa una arquitectura de **microservicios distribuidos** desacoplados, donde cada servicio es autónomo, gestiona su propio almacenamiento de datos y se comunica mediante combinaciones de API REST e intercambio asíncrono de eventos sobre RabbitMQ.

```mermaid
flowchart TB
    subgraph Clientes["Clientes"]
        SPA["Frontend SPA (React + TypeScript)"]
    end

    subgraph Perimetro["Perímetro y Seguridad"]
        GW["API Gateway (Spring Cloud Gateway)"]
        KC["Keycloak (IAM - OIDC / OAuth2)"]
    end

    subgraph Microservicios["Servicios de Negocio (Java 25 / Spring Boot / Spring MVC)"]
        CatalogSvc["Catalog Service\n(Catálogo y Precios)"]
        InventorySvc["Inventory Service\n(Stock y Reservas 15 min)"]
        OrderSvc["Order Service\n(Carrito, Pedidos y Saga)"]
        PaymentSvc["Payment Service\n(Pagos, Idempotencia, Reembolsos)"]
        NotifSvc["Notification Service\n(Notificaciones Asíncronas)"]
    end

    subgraph Mensajeria["Mensajería Asíncrona"]
        RMQ["RabbitMQ\n(Broker AMQP + DLX)"]
    end

    subgraph BasesDeDatos["Almacenamiento (PostgreSQL / Neon - Database-per-Service)"]
        DBCatalog[("catalog_db")]
        DBInventory[("inventory_db")]
        DBOrder[("order_db")]
        DBPayment[("payment_db")]
    end

    SPA -->|HTTPS / REST| GW
    SPA -.->|Autenticación OIDC| KC
    GW -.->|Validación JWT| KC
    
    GW -->|Enrutamiento /catalog| CatalogSvc
    GW -->|Enrutamiento /inventory| InventorySvc
    GW -->|Enrutamiento /orders| OrderSvc
    GW -->|Enrutamiento /payments| PaymentSvc

    CatalogSvc --> DBCatalog
    InventorySvc --> DBInventory
    OrderSvc --> DBOrder
    PaymentSvc --> DBPayment

    %% Saga y comunicación interna
    OrderSvc -->|Reserva / Confirmación| InventorySvc
    OrderSvc -->|Cobro / Reembolso| PaymentSvc

    %% Publicación Outbox y Consumo
    OrderSvc -.->|Transactional Outbox| RMQ
    InventorySvc -.->|Transactional Outbox| RMQ
    PaymentSvc -.->|Transactional Outbox| RMQ
    RMQ -.->|Eventos de Dominio| NotifSvc
    RMQ -.->|Eventos Compensación| OrderSvc
```

---

## 2. Responsabilidades y Límites de Contexto por Servicio

### 2.1 API Gateway (`api-gateway`) `[DECISIÓN APROBADA]`
- **Tecnología:** Java 25, Spring Boot, Spring Cloud Gateway (modelo reactivo no bloqueante en el perímetro).
- **Límites de Responsabilidad:**
  - Punto de entrada unificado para todas las solicitudes provenientes del frontend SPA.
  - Enrutamiento dinámico hacia los microservicios internos.
  - Terminación de políticas CORS y encabezados de seguridad HTTP.
  - Validación preliminar de tokens JWT emitidos por Keycloak (autenticación) y propagación del contexto de usuario (`userId`, `roles`, `email`) en cabeceras internas hacia los microservicios aguas abajo.
  - **No contiene lógica de negocio ni persistencia propia.**

### 2.2 Catalog Service (`catalog-service`) `[DECISIÓN APROBADA]`
- **Tecnología:** Java 25, Spring Boot, Spring MVC, Spring Data JPA, Flyway, PostgreSQL.
- **Límites de Responsabilidad:**
  - Gestión de categorías, marcas y catálogo de productos (SKU, nombre, descripción, imágenes).
  - Publicación y versionado de precios oficiales de venta en moneda **PEN**.
  - Exposición de endpoints públicos optimizados para consulta y búsqueda.
  - Operaciones de mutación (alta, edición, baja lógica) restringidas exclusivamente a usuarios con rol `ADMIN`.
  - **No gestiona inventario en tiempo real ni transacciones de compra.**

### 2.3 Inventory Service (`inventory-service`) `[DECISIÓN APROBADA]`
- **Tecnología:** Java 25, Spring Boot, Spring MVC, Spring Data JPA, Flyway, PostgreSQL.
- **Límites de Responsabilidad:**
  - Control de existencias físicas (`total_stock`) y existencias disponibles para venta (`available_stock`).
  - Gestión del ciclo de vida de **Reservas Temporales de Stock** con tiempo de expiración estricto de **15 minutos**.
  - Consolidación definitiva de deducción de stock ante confirmación de compra.
  - Liberación de reservas por cancelación o expiración de tiempo.
  - Registro de auditoría de movimientos de stock (Kardex: entradas, reservas, deducciones, reingresos).
  - **No procesa pagos ni conoce detalles de clientes o métodos de cobro.**

### 2.4 Order Service (`order-service`) `[DECISIÓN APROBADA]`
- **Tecnología:** Java 25, Spring Boot, Spring MVC, Spring Data JPA, Flyway, PostgreSQL.
- **Límites de Responsabilidad:**
  - Persistencia y gestión del Carrito de Compras de los clientes autenticados.
  - Creación y mantenimiento del ciclo de vida del Pedido (`Order`: `DRAFT`, `CHECKOUT_PENDING`, `CONFIRMED`, `IN_PREPARATION`, `SHIPPED`, `DELIVERED`, `CANCELLED`).
  - **Coordinación y Orquestación Persistente de la Saga de Checkout.**
  - Ejecución de la política de cancelación directa del cliente (exclusivamente permitida antes de `IN_PREPARATION`).
  - Cálculo de totales de compra incorporando la tarifa plana de envío de **S/ 10.00**.
  - Publicación de eventos de dominio a través del patrón **Transactional Outbox**.

### 2.5 Payment Service (`payment-service`) `[DECISIÓN APROBADA]`
- **Tecnología:** Java 25, Spring Boot, Spring MVC, Spring Data JPA, Flyway, PostgreSQL.
- **Límites de Responsabilidad:**
  - Procesamiento de cobros simulados mediante la interfaz `PaymentProviderGateway`.
  - Garantía de **idempotencia estricta** en cobros vía `Idempotency-Key` / tabla de control transaccional.
  - Manejo de estados de transacción: `INITIATED`, `PROCESSING`, `SUCCESS`, `DECLINED`, `UNCERTAIN`.
  - **Mecanismo de Conciliación Obligatoria** ante fallos de timeout o respuestas ambiguas.
  - Gestión del ciclo de vida de Reembolsos (`Refund`), requiriendo confirmación formal (`REFUND_CONFIRMED`) antes de marcarlos completados.
  - Reportabilidad financiera independiente: ventas brutas confirmadas separadas de débitos por reembolso.
  - **Cumplimiento de Seguridad (PCI-DSS Lite):** Prohibición estricta de persistir número de tarjeta completo (PAN) y CVV. Solo almacena token, marca y últimos 4 dígitos.

### 2.6 Notification Service (`notification-service`) `[DECISIÓN APROBADA]`
- **Tecnología:** Java 25, Spring Boot, Spring AMQP (RabbitMQ listener).
- **Límites de Responsabilidad:**
  - Consumidor puro y asíncrono de eventos de dominio publicados en RabbitMQ.
  - Envío simulado de correos transaccionales (confirmación de compra, cambios de estado logístico, notificación de reembolso).
  - Implementación desacoplada y tolerante a fallos: caídas en el servicio de notificaciones no detienen la operación de la tienda ni la Saga.
  - Registro estructurado de notificaciones emitidas para auditoría.

---

## 3. Patrones de Arquitectura Distribuida Adoptados

### 3.1 Autonomía de Datos (Database-per-Service) `[DECISIÓN APROBADA]`
- Cada microservicio opera con su propia base de datos o esquema lógicamente aislado en PostgreSQL / Neon.
- Se elimina cualquier dependencia de acoplamiento a nivel de base de datos (cero *cross-database foreign keys*, cero vistas compartidas, cero *distributed transactions* 2PC/XA).
- Si un servicio requiere información de otro, la solicita vía API interna o la mantiene sincronizada mediante eventos de dominio.

### 3.2 Transactional Outbox Pattern `[DECISIÓN APROBADA]`
Para evitar el problema de la doble escritura (*dual-write problem*) donde la base de datos se actualiza pero el mensaje en RabbitMQ se pierde por caída de red, se adopta el patrón Transactional Outbox:
1. Dentro de la transacción local de base de datos del microservicio, el agregado muta y se inserta un registro en la tabla `outbox_events`.
2. Ambas operaciones ocurren de forma atómica en PostgreSQL.
3. Un proceso secundario asíncrono (relayer/poller o Change Data Capture) lee los eventos pendientes de la tabla outbox y los publica en el Exchange correspondiente de RabbitMQ.
4. Tras confirmación del broker (RabbitMQ Publisher Confirms), el evento en outbox se marca como publicado o se elimina.

```mermaid
sequenceDiagram
    participant App as Capa de Aplicación
    participant DB as PostgreSQL (Local Tx)
    participant Relay as Outbox Poller
    participant Broker as RabbitMQ Broker

    App->>DB: BEGIN TRANSACTION
    App->>DB: Actualizar Agregado (ej. Order CONFIRMED)
    App->>DB: INSERT INTO outbox_events (id, topic, payload)
    App->>DB: COMMIT TRANSACTION
    
    Relay->>DB: SELECT * FROM outbox_events WHERE status = 'PENDING'
    Relay->>Broker: AMQP basic.publish(exchange, routingKey, payload)
    Broker-->>Relay: ACK (Publisher Confirm)
    Relay->>DB: UPDATE outbox_events SET status = 'PUBLISHED' WHERE id = ...
```

### 3.3 Consumidores Idempotentes (Idempotent Consumer Pattern) `[DECISIÓN APROBADA]`
- Todo consumidor en RabbitMQ implementa verificación previa de deduplicación.
- Se mantiene una tabla `processed_messages (message_id, consumer_name, processed_at)` en la base de datos del servicio receptor.
- Si un mensaje con el mismo ID arriba por reintento de RabbitMQ, el consumidor detecta su registro previo y descarta el procesamiento evitando efectos duplicados (*at-least-once to effectively-once processing*).

### 3.4 Manejo de Errores y Dead Lettering `[DECISIÓN APROBADA]`
- Errores transitorios (ej. timeout de red temporal) se reintentan con *Exponential Backoff*.
- Errores no recuperables (ej. payload corrupto, violación de regla de negocio no contemplada) se enrutan a un **Dead Letter Exchange (DLX)** y su correspondiente cola de mensajes muertos (**DLQ**) para inspección y reprocesamiento manual.

---

## 4. Seguridad, Autenticación y Autorización (Keycloak + OIDC) `[DECISIÓN APROBADA]`

- **Proveedor de Identidad:** Keycloak centraliza la gestión de usuarios, credenciales y asignación de roles.
- **Protocolo:** OpenID Connect (OIDC) / OAuth 2.0 con flujo Authorization Code con PKCE para el frontend SPA.
- **Token JWT:** El cliente envía el Bearer Token al API Gateway en cada solicitud HTTP.
- **Control de Acceso Basado en Roles (RBAC):**
  - Rol `CUSTOMER`: Otorgado automáticamente por el registro público. Puede gestionar su propio carrito, órdenes y cancelaciones tempranas.
  - Rol `ADMIN`: Asignado exclusivamente mediante provisión administrativa en Keycloak. Puede alterar el catálogo, modificar existencias de inventario y actualizar estados de pedidos.
- **Protección de Datos Financieros:** En ningún punto del ciclo de vida se recolectan o transmiten credenciales de pago completas sin tokenizar, eliminando el riesgo de fuga de datos de tarjetas.
