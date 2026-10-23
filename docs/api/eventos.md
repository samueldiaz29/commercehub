# Especificación de Eventos y Mensajería Asíncrona — CommerceHub

> [!NOTE]
> **Aviso de Estado:** Los nombres de tópicos, routing keys y estructuras de eventos detalladas en este documento constituyen una **`[PROPUESTA TÉCNICA DE EVENTOS ASÍNCRONOS]`** en RabbitMQ. Los principios de Transactional Outbox, publicación garantizada y consumidores idempotentes representan **`[DECISIONES APROBADAS]`**.

---

## 1. Topología de RabbitMQ Propuesta

Se propone una topología basada en un intercambio central de tipo `topic`, que permite un enrutamiento selectivo y flexible desacoplando publicadores y suscriptores.

```mermaid
flowchart LR
    subgraph Publicadores["Transactional Outbox (Publicadores)"]
        OrderSvc["Order Service"]
        InvSvc["Inventory Service"]
        PaySvc["Payment Service"]
    end

    subgraph Broker["RabbitMQ"]
        TopicEx["Topic Exchange:\ncommercehub.events"]
        DLX["Dead Letter Exchange:\ncommercehub.dlx"]
        
        QNotif["Queue:\nnotif.events.queue\n(Key: *.confirmed, *.status-changed)"]
        QOrderComp["Queue:\norder.compensation.queue\n(Key: inventory.reservation-expired)"]
        QDLQ["Queue:\ncommercehub.dead-letter.queue"]
    end

    subgraph Consumidores["Servicios Consumidores"]
        NotifSvc["Notification Service"]
        OrderSvcCons["Order Service\n(Saga Compensator)"]
        AdminAudit["Auditoría / DLQ Worker"]
    end

    OrderSvc -->|Publish| TopicEx
    InvSvc -->|Publish| TopicEx
    PaySvc -->|Publish| TopicEx

    TopicEx -->|Routing: order.*| QNotif
    TopicEx -->|Routing: payment.*| QNotif
    TopicEx -->|Routing: inventory.reservation-expired| QOrderComp

    QNotif -.->|Fallo persistente (DLX)| DLX
    QOrderComp -.->|Fallo persistente (DLX)| DLX
    DLX --> QDLQ

    QNotif --> NotifSvc
    QOrderComp --> OrderSvcCons
    QDLQ --> AdminAudit
```

### Componentes de la Topología:
1. **Exchange Principal:** `commercehub.events` (Tipo: `topic`, Durable).
2. **Exchange de Mensajes Muertos (DLX):** `commercehub.dlx` (Tipo: `fanout` o `direct`, Durable).
3. **Colas por Consumidor:** Cada microservicio declara sus propias colas con argumentos de Dead Lettering configurados (`x-dead-letter-exchange: commercehub.dlx`).

---

## 2. Esquema Estándar de Sobre de Evento (`EventEnvelope<T>`) `[PROPUESTA PENDIENTE]`

Para uniformizar la serialización y garantizar observabilidad, todo mensaje emitido a RabbitMQ debe implementar el siguiente envoltorio JSON inspirado en CloudEvents:

```json
{
  "event_id": "evt-7a1b8c2d-3e4f-5a6b-7c8d-9e0f1a2b3c4d",
  "event_type": "commercehub.order.confirmed",
  "event_version": "1.0",
  "aggregate_type": "Order",
  "aggregate_id": "ord-7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "occurred_at": "2026-10-22T20:30:15Z",
  "correlation_id": "c1f7a83d-6b08-4e1b-9f6b-87b649a4921c",
  "payload": { }
}
```

---

## 3. Catálogo de Eventos de Dominio Propuestos

### 3.1 Dominio de Pedidos (`order.*`)

#### `commercehub.order.confirmed` `[PROPUESTA PENDIENTE]`
- **Publicador:** `order-service` (vía Transactional Outbox tras éxito en Saga).
- **Consumidores:** `notification-service`.
- **Routing Key:** `order.confirmed`
- **Estructura del Payload:**
```json
{
  "order_id": "ord-7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "customer_id": "usr-5678",
  "customer_email": "cliente@example.pe",
  "total_amount": 6509.00,
  "currency": "PEN",
  "shipping_fee": 10.00,
  "items": [
    {
      "product_id": "prod-e3b0c442",
      "name": "Laptop Pro 14",
      "quantity": 1,
      "unit_price": 6499.00
    }
  ],
  "shipping_address": {
    "street": "Av. Javier Prado Este 2465",
    "district": "San Borja",
    "city": "Lima"
  }
}
```

#### `commercehub.order.status-changed` `[PROPUESTA PENDIENTE]`
- **Publicador:** `order-service`.
- **Consumidores:** `notification-service`.
- **Routing Key:** `order.status-changed`
- **Estructura del Payload:**
```json
{
  "order_id": "ord-7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "customer_id": "usr-5678",
  "previous_status": "CONFIRMED",
  "new_status": "IN_PREPARATION",
  "notes": "En empaque en almacén"
}
```

#### `commercehub.order.cancelled` `[PROPUESTA PENDIENTE]`
- **Publicador:** `order-service`.
- **Consumidores:** `inventory-service`, `payment-service`, `notification-service`.
- **Routing Key:** `order.cancelled`
- **Estructura del Payload:**
```json
{
  "order_id": "ord-7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "customer_id": "usr-5678",
  "reason": "CLIENT_CANCELLED_BEFORE_PREPARATION",
  "cancelled_at": "2026-10-22T21:00:00Z"
}
```

---

### 3.2 Dominio de Inventario (`inventory.*`)

#### `commercehub.inventory.reservation-expired` `[PROPUESTA PENDIENTE]`
- **Publicador:** `inventory-service` (vía scheduler de expiración de 15 min).
- **Consumidores:** `order-service` (para compensar y cerrar la orden).
- **Routing Key:** `inventory.reservation-expired`
- **Estructura del Payload:**
```json
{
  "reservation_id": "res-9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "order_id": "ord-7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "expired_at": "2026-10-22T20:45:00Z"
}
```

#### `commercehub.inventory.stock-depleted` `[PROPUESTA PENDIENTE]`
- **Publicador:** `inventory-service`.
- **Consumidores:** `catalog-service` (para marcar producto como sin stock referencial).
- **Routing Key:** `inventory.stock-depleted`
- **Estructura del Payload:**
```json
{
  "product_id": "prod-e3b0c442",
  "sku": "LAP-MAC-14M3",
  "available_stock": 0
}
```

---

### 3.3 Dominio de Pagos (`payment.*`)

#### `commercehub.payment.refund-confirmed` `[PROPUESTA PENDIENTE]`
- **Publicador:** `payment-service` (tras confirmación formal del procesador de pagos).
- **Consumidores:** `order-service`, `notification-service`.
- **Routing Key:** `payment.refund-confirmed`
- **Estructura del Payload:**
```json
{
  "refund_id": "ref-5e4a3b2c-9911-4def-9988-1234567890ab",
  "payment_id": "pay-3c1e2a87-1425-498b-944b-e07fc1f90999",
  "order_id": "ord-7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "amount": 6509.00,
  "currency": "PEN",
  "confirmed_at": "2026-10-22T21:05:00Z"
}
```

---

## 4. Política de Reintentos, Deduplicación y Dead Letter Queue (DLQ) `[DECISIÓN APROBADA]`

1. **Deduplicación en Consumidor:** Todo consumidor verifica el `event_id` contra su tabla local `processed_messages`. Si ya existe, se efectúa un ACK inmediato a RabbitMQ y se ignora el payload.
2. **Reintentos con Backoff Exponencial:** Si el consumidor arroja una excepción recuperable (ej. caída transitoria de base de datos), el contenedor de escucha de Spring AMQP reintenta hasta 3 veces con intervalos exponenciales (1s, 2s, 4s).
3. **Enrutamiento a DLQ:** Si el mensaje agota los reintentos o lanza una excepción fatal de validación de negocio (no recuperable), es rechazado con `basic.reject(requeue = false)` y transferido automáticamente por RabbitMQ a `commercehub.dead-letter.queue` para diagnóstico.
