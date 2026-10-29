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

#### `GET /api/v1/products`
- **Propósito:** Consulta pública paginada de productos publicados (`published = true`).
- **Acceso:** Público (anónimo / autenticado).
- **Parámetros de consulta (Query Params):** `page` (base cero, predeterminado `0`), `size` (predeterminado `20`, mínimo `1`), `category_id` (opcional, ID numérico de categoría), `search` (opcional, coincidencia parcial sin distinguir mayúsculas/minúsculas en el nombre).
- **Orden:** nombre ascendente y, para desempate estable, ID ascendente. No se acepta un parámetro de ordenamiento.
- **Identificadores:** numéricos `BIGINT`, como define el esquema persistente del catálogo.
- **Respuesta Exitosa (HTTP 200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "sku": "LAP-MAC-14M3",
      "name": "Laptop Pro 14",
      "description": "Chip M3, 16GB RAM, 512GB SSD",
      "category": { "id": 1, "name": "Laptops" },
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

#### `GET /api/v1/products/{id}`
- **Propósito:** Obtener el detalle de un producto publicado.
- **Acceso:** Público (anónimo / autenticado).
- **Respuesta exitosa (HTTP 200 OK):** El mismo DTO de producto incluido en `content` del listado.
- **Producto inexistente o no publicado (HTTP 404 Not Found):** Problem Details. Ambos casos tienen la misma respuesta para no revelar productos ocultos.

#### `GET /api/v1/categories`
- **Propósito:** Consultar las categorías que contienen al menos un producto publicado.
- **Acceso:** Público (anónimo / autenticado).
- **Respuesta exitosa (HTTP 200 OK):** Arreglo ordenado por nombre ascendente y luego ID ascendente, con elementos `{ "id": 1, "name": "Laptops" }`.

#### Errores de consulta del catálogo
- Parámetros inválidos o `page < 0` / `size < 1`: HTTP `400 Bad Request` con Problem Details.
- `X-Correlation-Id`, cuando se recibe, se devuelve como `correlation_id`; todas las respuestas de error incluyen `type`, `title`, `status`, `detail`, `instance` y `timestamp`.

#### `POST /api/v1/admin/products`
- **Propósito:** Crear un producto en el catálogo.
- **Acceso:** JWT válido emitido por el issuer Keycloak configurado y rol `ADMIN` en `realm_access.roles`. Los roles se autorizan en el servidor.
- **Cuerpo de Petición (Request Body):**
```json
{
  "sku": "AUD-SONY-WH1000",
  "name": "Audífonos Inalámbricos Noise Cancelling",
  "description": "Cancelación de ruido activa, batería 30h",
  "category_id": 3,
  "price": 1299.00,
  "currency": "PEN",
  "image_url": "https://assets.commercehub.local/products/sony-wh.webp",
  "published": true
}
```
- `sku` y `name` son obligatorios y no pueden estar en blanco; sus longitudes máximas son 64 y 200. `category_id` debe ser el ID numérico de una categoría existente. `price` debe ser positivo y caber en `NUMERIC(19,2)`. La moneda admitida es `PEN`. `description` y `image_url` son opcionales y se limitan a las longitudes del esquema. `published` es opcional y su valor predeterminado es `false`.
- **Respuesta (HTTP 201 Created):** Retorna el DTO de producto documentado para consultas, con `Location` apuntando a `/api/v1/admin/products/{id}`.
- SKU duplicado (incluidas colisiones detectadas al persistir): HTTP `409 Conflict` con Problem Details. Categoría inexistente: HTTP `404 Not Found`. Cuerpo inválido o precio no permitido: HTTP `400 Bad Request` con Problem Details.
- Usuario anónimo: HTTP `401 Unauthorized`. Usuario autenticado sin rol `ADMIN` (incluido `CUSTOMER`): HTTP `403 Forbidden`.

#### `PUT /api/v1/admin/products/{id}`
- **Propósito:** Actualizar los campos comerciales editables de un producto existente.
- **Acceso:** JWT válido del issuer configurado y rol `ADMIN` en `realm_access.roles`.
- **Request Body:** Mismos campos que la creación excepto `published`: `sku`, `name`, `description`, `category_id`, `price`, `currency` y `image_url`. SKU, nombre, categoría, precio y moneda son obligatorios; `description` e `image_url` son opcionales. Las reglas de longitud, SKU único, categoría existente y precio PEN son iguales a `POST`.
- **Campos administrados por el sistema:** El DTO no expone `id` ni `published`; el identificador nunca se toma del cuerpo y la actualización conserva el estado de publicación actual. La moneda admitida sigue siendo únicamente `PEN`.
- **Respuesta (HTTP 200 OK):** DTO completo de producto (`ProductResponse`) con los datos actualizados.
- Producto inexistente: `404 Not Found`; categoría inexistente: `404 Not Found`; SKU ocupado por otro producto: `409 Conflict`; cuerpo inválido: `400 Bad Request`. Los errores usan Problem Details.

#### `PATCH /api/v1/admin/products/{id}`
- **Propósito:** Deshabilitar un producto mediante baja lógica.
- **Acceso:** JWT válido del issuer configurado y rol `ADMIN` en `realm_access.roles`.
- **Request Body:** `{ "published": false }`. El cuerpo es obligatorio y no se acepta `true` en esta operación.
- **Respuesta (HTTP 200 OK):** DTO del producto con `published: false`. La operación es idempotente: repetirla sobre el mismo producto devuelve `200` y mantiene el producto deshabilitado.
- Producto inexistente: `404 Not Found`; cuerpo inválido o `published` distinto de `false`: `400 Bad Request`, ambos con Problem Details.
- La deshabilitación persiste `published = false` sin borrar el producto ni alterar carritos u órdenes. Desde el commit de la operación el producto queda excluido del listado, las búsquedas y el detalle público; su detalle responde `404` como cualquier producto no publicado.

#### Administración independiente de categorías
- HU-CAT-02 solo menciona gestionar categorías en el objetivo general y la categorización de productos; no define criterios para crear, editar, deshabilitar o eliminar categorías. El contrato existente únicamente define `GET /api/v1/categories` público. No se agrega CRUD administrativo de categorías ni una política de eliminación. `POST` y `PUT` de productos requieren `category_id` correspondiente a una categoría existente.

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

#### HU-CART-01 — Carrito persistente

Todas las rutas de carrito requieren JWT válido emitido por el issuer Keycloak configurado y rol `CUSTOMER` en `realm_access.roles`. El propietario siempre se obtiene del claim `sub`; el contrato no acepta `user_id`/`customer_id` para identificarlo. Los identificadores de producto son los IDs numéricos `BIGINT` del catálogo. El carrito ausente se representa como carrito vacío (HTTP 200) y se persiste al agregar el primer artículo.

##### `GET /api/v1/cart`
- **Propósito:** Consultar el carrito propio y recalcular importes en PEN desde los precios actuales del catálogo.
- **Respuesta (HTTP 200 OK):**
```json
{
  "currency": "PEN",
  "items": [{ "product_id": 1, "quantity": 2, "unit_price": 25.50, "line_subtotal": 51.00, "currency": "PEN" }],
  "subtotal": 51.00,
  "shipping": 10.00,
  "total": 61.00
}
```
Un carrito vacío devuelve `items: []` y subtotal, envío y total `0.00`.

##### `POST /api/v1/cart/items`
- **Propósito:** Agregar un producto publicado. El request contiene `{ "product_id": 1, "quantity": 2 }`. `quantity` es entero y mínimo 1. Repetir el producto incrementa la cantidad existente.
- **Respuesta:** HTTP 200 con el resumen recalculado.
- Producto inexistente/no publicado: `404`; cantidad/cuerpo inválido: `400`; fallo o indisponibilidad del catálogo: `503`, todos con Problem Details.
- Conflicto por cambios concurrentes del carrito o por una creación simultánea: `409 Conflict` con Problem Details genérico; el cliente puede volver a consultar y reintentar la operación.

##### `PATCH /api/v1/cart/items/{productId}`
- **Propósito:** Reemplazar la cantidad del artículo existente. Request `{ "quantity": 3 }`, mínimo 1.
- **Respuesta:** HTTP 200 con el resumen recalculado. Carrito/artículo inexistente: `404`; cantidad inválida: `400`.
- Conflicto de integridad/concurrencia: `409 Conflict` con Problem Details.

##### `DELETE /api/v1/cart/items/{productId}`
- **Propósito:** Retirar un artículo del carrito propio.
- **Respuesta:** HTTP `200` con el resumen recalculado de los artículos restantes. Artículo inexistente: `404`.
- Conflicto de integridad/concurrencia: `409 Conflict` con Problem Details.

Usuario anónimo: `401`; JWT autenticado sin `CUSTOMER` (incluido `ADMIN` sin dicho rol): `403`. Las respuestas usan DTOs y Problem Details RFC 9457. El carrito guarda únicamente ID de producto y cantidad; no reserva ni descuenta inventario. No existe FK hacia catálogo por la autonomía de bases de datos.

**Precio pendiente de decisión:** el resumen consulta el endpoint público `GET /api/v1/products/{id}` de `catalog-service` y usa el precio PEN vigente para cada respuesta. No se persiste/congela precio ni se define una política para cambios de precio al checkout. Esta implementación hace posible el importe actual del resumen, pero no resuelve DP-01; se requiere decisión antes de implementar checkout.

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
