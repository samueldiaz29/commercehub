# Contratos de API REST — CommerceHub

> [!NOTE]
> **Aviso de Estado:** Los nombres de endpoints, estructuras de datos JSON y códigos de retorno especificados en este documento constituyen una **`[PROPUESTA TÉCNICA DE CONTRATO REST]`**. Deben ser revisados y validados antes de iniciar la implementación de cada servicio. Las reglas de negocio y restricciones que los respaldan representan **`[DECISIONES APROBADAS]`**.

---

## 1. Convenciones Globales y Encabezados Estándar

### 1.1 Encabezados HTTP Comunes
- `Authorization`: `Bearer <JWT>` (Token de identidad OIDC emitido por Keycloak).
- `X-Correlation-Id`: Identificador UUID generado por el Gateway o cliente para trazabilidad distribuida transversal de la solicitud.
- `Idempotency-Key`: Clave UUID obligatoria en todas las peticiones `POST` de mutación crítica (checkout, pagos, reembolsos) para garantizar idempotencia.
- `Content-Type`: `application/json`.
- `Accept`: `application/json`.

### 1.2 Formato de Errores Estándar (RFC 7807 / RFC 9457 Problem Details)
Todas las respuestas con código de error (HTTP 4xx / 5xx) utilizarán el esquema estructurado estándar:

```json
{
  "type": "https://commercehub.local/errors/inventory-insufficient",
  "title": "Stock Insuficiente",
  "status": 409,
  "detail": "El producto con SKU 'TECH-KEYB-01' no cuenta con 3 unidades disponibles.",
  "instance": "/api/v1/checkout",
  "timestamp": "2026-10-22T20:30:00Z",
  "correlation_id": "c1f7a83d-6b08-4e1b-9f6b-87b649a4921c"
}
```

---

## 2. Contratos Propuestos por Microservicio

### 2.1 Catalog Service (`catalog-service`)

#### `GET /api/v1/products` `[PROPUESTA PENDIENTE]`
- **Propósito:** Consulta pública de productos activos.
- **Acceso:** Público (anónimo / autenticado).
- **Parámetros de consulta (Query Params):** `page`, `size`, `category_id`, `search`.
- **Respuesta Exitosa (HTTP 200 OK):**
```json
{
  "content": [
    {
      "id": "prod-e3b0c442-98fc-1c14-9afbf4c8996f",
      "sku": "LAP-MAC-14M3",
      "name": "Laptop Pro 14",
      "description": "Chip M3, 16GB RAM, 512GB SSD",
      "category": { "id": "cat-01", "name": "Laptops" },
      "price": 6499.00,
      "currency": "PEN",
      "image_url": "https://assets.commercehub.local/products/mac14.webp",
      "published": true
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 1,
  "total_pages": 1
}
```

#### `POST /api/v1/admin/products` `[PROPUESTA PENDIENTE]`
- **Propósito:** Creación de un producto en el catálogo.
- **Acceso:** Restringido a rol `ADMIN`.
- **Cuerpo de Petición (Request Body):**
```json
{
  "sku": "AUD-SONY-WH1000",
  "name": "Audífonos Inalámbricos Noise Cancelling",
  "description": "Cancelación de ruido activa, batería 30h",
  "category_id": "cat-audio",
  "price": 1299.00,
  "currency": "PEN",
  "image_url": "https://assets.commercehub.local/products/sony-wh.webp"
}
```
- **Respuesta (HTTP 201 Created):** Retorna el producto creado con su identificador generado.

---

### 2.2 Inventory Service (`inventory-service`)

#### `POST /api/v1/internal/inventory/reservations` `[PROPUESTA PENDIENTE]`
- **Propósito:** Reservar unidades de stock temporalmente durante el checkout (TTL 15 min).
- **Acceso:** Interno (invocado por `order-service`).
- **Headers:** `Idempotency-Key` (o `X-Order-Id`).
- **Request Body:**
```json
{
  "order_id": "ord-7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "ttl_minutes": 15,
  "items": [
    {
      "product_id": "prod-e3b0c442-98fc-1c14-9afbf4c8996f",
      "quantity": 1
    }
  ]
}
```
- **Respuesta Exitosa (HTTP 201 Created):**
```json
{
  "reservation_id": "res-9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "order_id": "ord-7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "RESERVED",
  "expires_at": "2026-10-22T20:45:00Z"
}
```
- **Respuesta de Falla por Stock Insuficiente (HTTP 409 Conflict):**
```json
{
  "type": "https://commercehub.local/errors/stock-unavailable",
  "title": "Stock Insuficiente",
  "status": 409,
  "detail": "El producto prod-e3b0c442 no cuenta con unidades disponibles para reserva."
}
```

#### `POST /api/v1/internal/inventory/reservations/{reservationId}/confirm` `[PROPUESTA PENDIENTE]`
- **Propósito:** Consolidar la reserva temporal como deducción definitiva del stock físico tras el pago.
- **Acceso:** Interno (`order-service`).
- **Respuesta (HTTP 200 OK):** `{ "reservation_id": "res-9b1deb4d", "status": "CONFIRMED" }`.

#### `POST /api/v1/internal/inventory/reservations/{reservationId}/release` `[PROPUESTA PENDIENTE]`
- **Propósito:** Liberar unidades reservadas por compensación de la Saga.
- **Acceso:** Interno (`order-service`).
- **Respuesta (HTTP 200 OK):** `{ "reservation_id": "res-9b1deb4d", "status": "RELEASED" }`.

---

### 2.3 Order Service (`order-service`)

#### `GET /api/v1/cart` `[PROPUESTA PENDIENTE]`
- **Propósito:** Obtener el carrito del usuario autenticado.
- **Acceso:** `CUSTOMER`.
- **Respuesta Exitosa (HTTP 200 OK):**
```json
{
  "cart_id": "cart-1234",
  "customer_id": "usr-5678",
  "items": [
    {
      "item_id": "ci-01",
      "product_id": "prod-e3b0c442-98fc-1c14-9afbf4c8996f",
      "product_name": "Laptop Pro 14",
      "unit_price": 6499.00,
      "quantity": 1,
      "subtotal": 6499.00
    }
  ],
  "subtotal_amount": 6499.00,
  "shipping_fee": 10.00,
  "total_amount": 6509.00,
  "currency": "PEN"
}
```

#### `POST /api/v1/checkout` `[PROPUESTA PENDIENTE]`
- **Propósito:** Iniciar la Saga de Checkout con los datos de entrega y el método de pago simulado.
- **Acceso:** `CUSTOMER`.
- **Headers Obligatorios:** `Idempotency-Key: <UUID>`.
- **Request Body:**
```json
{
  "shipping_address": {
    "street": "Av. Javier Prado Este 2465",
    "district": "San Borja",
    "city": "Lima",
    "country": "PE",
    "postal_code": "15037",
    "receiver_name": "Juan Pérez",
    "phone": "+51999888777"
  },
  "payment_method": {
    "provider": "SIMULATED",
    "card_token": "tok_sim_visa_valid",
    "simulation_outcome": "SUCCESS"
  }
}
```
- **Respuesta Exitosa (HTTP 201 Created):**
```json
{
  "order_id": "ord-7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "CONFIRMED",
  "subtotal": 6499.00,
  "shipping_fee": 10.00,
  "total_amount": 6509.00,
  "currency": "PEN",
  "created_at": "2026-10-22T20:30:00Z"
}
```

#### `POST /api/v1/orders/{orderId}/cancel` `[PROPUESTA PENDIENTE]`
- **Propósito:** Cancelación directa por parte del cliente.
- **Acceso:** `CUSTOMER` (dueño de la orden).
- **Regla de Validación:** Rechaza con HTTP 409 Conflict si la orden ya se encuentra en `IN_PREPARATION`, `SHIPPED` o `DELIVERED`.
- **Respuesta (HTTP 200 OK):**
```json
{
  "order_id": "ord-7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "CANCELLED",
  "message": "Su pedido ha sido cancelado exitosamente y el proceso de reembolso ha sido iniciado."
}
```

#### `PATCH /api/v1/admin/orders/{orderId}/status` `[PROPUESTA PENDIENTE]`
- **Propósito:** Actualización del estado logístico del pedido por personal de almacén/operaciones.
- **Acceso:** `ADMIN`.
- **Request Body:**
```json
{
  "new_status": "IN_PREPARATION",
  "notes": "Asignado a mesa de embalaje 3"
}
```
- **Respuesta (HTTP 200 OK):** Retorna la orden con el estado actualizado.

---

### 2.4 Payment Service (`payment-service`)

#### `POST /api/v1/internal/payments/authorize-and-capture` `[PROPUESTA PENDIENTE]`
- **Propósito:** Autorizar y liquidar el cobro en el simulador de pagos.
- **Acceso:** Interno (`order-service`).
- **Headers:** `Idempotency-Key: <UUID>`.
- **Request Body:**
```json
{
  "order_id": "ord-7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "amount": 6509.00,
  "currency": "PEN",
  "customer_id": "usr-5678",
  "payment_method": {
    "card_token": "tok_sim_visa_valid",
    "card_last4": "4242",
    "card_brand": "VISA",
    "simulation_outcome": "SUCCESS"
  }
}
```
- **Respuesta Exitosa (HTTP 200 OK):**
```json
{
  "payment_id": "pay-3c1e2a87-1425-498b-944b-e07fc1f90999",
  "order_id": "ord-7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "SUCCESS",
  "transaction_reference": "SIM-REF-998811",
  "amount": 6509.00,
  "currency": "PEN",
  "captured_at": "2026-10-22T20:30:15Z"
}
```
- **Respuesta de Rechazo Comercial (HTTP 422 Unprocessable Entity):**
```json
{
  "payment_id": "pay-3c1e2a87-1425-498b-944b-e07fc1f90999",
  "status": "DECLINED",
  "decline_code": "INSUFFICIENT_FUNDS",
  "message": "La tarjeta de prueba seleccionada no cuenta con fondos suficientes."
}
```

#### `POST /api/v1/internal/payments/{paymentId}/refund` `[PROPUESTA PENDIENTE]`
- **Propósito:** Emitir un reembolso sobre un pago exitoso previo.
- **Acceso:** Interno (`order-service`) o `ADMIN`.
- **Headers:** `Idempotency-Key: <UUID>`.
- **Request Body:**
```json
{
  "amount": 6509.00,
  "currency": "PEN",
  "reason": "CLIENT_CANCELLATION_BEFORE_PREPARATION"
}
```
- **Respuesta (HTTP 200 OK):**
```json
{
  "refund_id": "ref-5e4a3b2c-9911-4def-9988-1234567890ab",
  "payment_id": "pay-3c1e2a87-1425-498b-944b-e07fc1f90999",
  "status": "CONFIRMED",
  "amount": 6509.00,
  "currency": "PEN",
  "confirmed_at": "2026-10-22T20:35:00Z"
}
```

#### `GET /api/v1/internal/payments/{paymentId}/status` `[PROPUESTA PENDIENTE]`
- **Propósito:** Consultar el estado de una transacción para tareas de conciliación de pagos con resultado incierto (`UNCERTAIN`).
- **Acceso:** Interno (Job de Conciliación).
