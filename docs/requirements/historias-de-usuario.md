# Requisitos y Historias de Usuario — CommerceHub

Este documento compila las Historias de Usuario (HU) y sus Criterios de Aceptación para CommerceHub. Todas las historias se derivan de las decisiones de negocio aprobadas y los límites de contexto de los microservicios.

---

## 1. Matriz de Roles y Actores

- **Cliente No Autenticado (Visitante):** Puede explorar el catálogo público y registrarse en la plataforma.
- **Cliente Autenticado (`CUSTOMER`):** Usuario registrado a través del registro público. Puede gestionar su carrito, realizar checkouts, pagar con simulador, consultar su historial y cancelar pedidos antes de su preparación.
- **Administrador (`ADMIN`):** Usuario con privilegios de gestión provisto internamente (no disponible en registro público). Administra productos, categorías, existencias de inventario, consulta reportes de ventas y actualiza estados de preparación y despacho de pedidos.

---

## 2. Épica 1: Catálogo y Búsqueda de Productos

### HU-CAT-01: Exploración Pública del Catálogo `[DECISIÓN APROBADA]`
**Como** visitante o cliente autenticado,  
**Quiero** visualizar la lista de productos activos organizados por categorías y consultar sus detalles,  
**Para** seleccionar los artículos que deseo comprar.

**Criterios de Aceptación:**
1. Solo se muestran los productos con estado activo (`published = true`).
2. Cada producto expone su nombre, descripción, categoría, SKU, imagen referencial y precio en moneda **PEN (S/)**.
3. El catálogo no gestiona el stock definitivo en tiempo real, pero muestra un indicador referencial de disponibilidad provisto por el servicio de inventario o cacheado.
4. Soporte para filtrado por categoría y búsqueda textual por nombre.

### HU-CAT-02: Gestión Administrativa de Productos y Categorías `[DECISIÓN APROBADA]`
**Como** administrador del sistema (`ADMIN`),  
**Quiero** dar de alta, editar, categorizar y deshabilitar productos,  
**Para** mantener actualizada la oferta comercial de la tienda.

**Criterios de Aceptación:**
1. Solo usuarios autenticados con rol `ADMIN` pueden invocar los endpoints de creación/actualización.
2. Todo producto requiere nombre, SKU único, categoría válida, precio base positivo en PEN y estado.
3. Deshabilitar un producto lo oculta inmediatamente de las búsquedas públicas, pero no altera los carritos ni pedidos ya en curso.

---

## 3. Épica 2: Carrito de Compras

### HU-CART-01: Gestión de Artículos en el Carrito `[DECISIÓN APROBADA]`
**Como** cliente autenticado,  
**Quiero** agregar, modificar cantidades y eliminar productos en mi carrito de compras,  
**Para** preparar la selección de artículos antes de iniciar la compra.

**Criterios de Aceptación:**
1. El carrito persiste en base de datos (`order-service`) asociado al identificador único del cliente autenticado.
2. No se permite agregar cantidades menores a 1.
3. Agregar un artículo al carrito **NO** reserva inventario de forma inmediata (la reserva ocurre exclusivamente al iniciar el checkout).
4. El resumen del carrito desglosa el subtotal de artículos, el costo de envío fijo de **S/ 10.00** y el total calculado en PEN.

---

## 4. Épica 3: Proceso de Checkout y Saga de Compra

### HU-CHK-01: Inicio de Checkout y Reserva Temporal de Stock `[DECISIÓN APROBADA]`
**Como** cliente con un carrito con artículos,  
**Quiero** iniciar el checkout ingresando mi dirección de entrega,  
**Para** asegurar temporalmente las unidades antes de efectuar el pago.

**Criterios de Aceptación:**
1. Al pulsar "Iniciar Checkout", el `order-service` crea un pedido en estado preliminar y solicita a `inventory-service` una reserva temporal de stock.
2. La reserva de inventario tiene una vigencia estricta de **15 minutos**.
3. Si algún ítem no cuenta con stock suficiente disponible, el checkout es rechazado inmediatamente indicando el producto agotado y no se crea ninguna reserva parcial.
4. El cliente dispone de un temporizador visible de 15 minutos para completar el pago.

### HU-CHK-02: Procesamiento del Pago Simulado e Idempotente `[DECISIÓN APROBADA]`
**Como** cliente en proceso de checkout,  
**Quiero** enviar los datos de pago utilizando el simulador de pasarela,  
**Para** completar la transacción de forma segura y sin duplicidad.

**Criterios de Aceptación:**
1. La petición de cobro incluye un identificador único de idempotencia (`Idempotency-Key` / `checkout_id`). Si se reenvía la solicitud por problemas de red o doble clic, el `payment-service` retorna el resultado existente sin generar un segundo cargo.
2. En cumplimiento de la política de seguridad, **no se almacena el número de tarjeta completo (PAN) ni el código CVV**. Solo se guardan los últimos 4 dígitos y el token de autorización generado.
3. El simulador de pagos permite simular escenarios explícitos:
   - **Éxito (Authorized/Captured):** Cargo aceptado.
   - **Rechazo (Declined):** Fondos insuficientes o tarjeta inválida.
   - **Error / Timeout (Uncertain):** Caída temporal o retardo simulado para validar conciliación.

### HU-CHK-03: Confirmación Exitosa de la Compra `[DECISIÓN APROBADA]`
**Como** cliente con pago aprobado,  
**Quiero** recibir la confirmación de mi pedido con su identificador y resumen,  
**Para** tener constancia de que mi compra fue aceptada formalmente.

**Criterios de Aceptación:**
1. Una venta se considera confirmada **únicamente** cuando el pago está confirmado y la reserva de inventario se consolida como deducción permanente de stock.
2. El pedido pasa a estado `CONFIRMED`.
3. El carrito del cliente se vacía automáticamente.
4. Se emite un evento asíncrono para que `notification-service` envíe el correo de confirmación de compra.

### HU-CHK-04: Compensación por Pago Rechazado o Expiración de Reserva `[DECISIÓN APROBADA]`
**Como** sistema de comercio electrónico,  
**Quiero** liberar automáticamente el inventario reservado si el pago falla o expira el tiempo límite de 15 minutos,  
**Para** no retener mercancía que otros clientes pueden comprar.

**Criterios de Aceptación:**
1. Si el pago es declinado por la pasarela, la Saga ejecuta la compensación: libera la reserva en `inventory-service` y marca el pedido como cancelado/rechazado.
2. Si el cliente no efectúa el pago dentro de la ventana de 15 minutos, la reserva expira en `inventory-service` y el pedido asociado se cancela por expiración.

---

## 5. Épica 4: Ciclo de Vida y Cancelaciones de Pedidos

### HU-ORD-01: Consulta de Pedidos del Cliente `[DECISIÓN APROBADA]`
**Como** cliente autenticado,  
**Quiero** consultar la lista y el detalle de mis pedidos realizados,  
**Para** conocer el estado actual de preparación, despacho o entrega.

**Criterios de Aceptación:**
1. El cliente solo puede visualizar sus propios pedidos (aislamiento por `userId` del JWT).
2. Se muestra el estado del pedido, fecha, desglose de ítems, costo de envío (S/ 10.00), monto total y dirección de entrega.

### HU-ORD-02: Cancelación Directa por el Cliente `[DECISIÓN APROBADA]`
**Como** cliente con un pedido confirmado,  
**Quiero** cancelar mi pedido de forma directa desde mi panel de usuario,  
**Para** recuperar mi dinero si cambié de opinión oportunamente.

**Criterios de Aceptación:**
1. La cancelación directa por el cliente **sólo está permitida antes de que el pedido entre en estado de preparación** (`IN_PREPARATION`).
2. Si el pedido ya pasó a `IN_PREPARATION`, `SHIPPED` o `DELIVERED`, la opción de cancelación directa queda deshabilitada para el cliente (requiere contactar soporte administrativo).
3. Al cancelarse con éxito, se inicia de forma automática el flujo de reembolso y reversión de inventario.

### HU-ORD-03: Gestión y Despacho de Pedidos por Administrador `[DECISIÓN APROBADA]`
**Como** administrador de tienda (`ADMIN`),  
**Quiero** avanzar el estado de los pedidos confirmados a preparación, despachado y entregado,  
**Para** controlar el flujo operativo de almacén y distribución.

**Criterios de Aceptación:**
1. El administrador puede consultar pedidos filtrados por estado.
2. El administrador puede ejecutar las transiciones: `CONFIRMED` $\to$ `IN_PREPARATION` $\to$ `SHIPPED` $\to$ `DELIVERED`.
3. Cada transición emite eventos de dominio para notificación al cliente.

---

## 6. Épica 5: Pagos, Conciliación y Reembolsos

### HU-PAY-01: Conciliación de Pagos con Resultado Incierto `[DECISIÓN APROBADA]`
**Como** operador de finanzas o sistema de monitoreo,  
**Quiero** que los pagos cuyo resultado fue ambiguo o terminaron en timeout sean conciliados obligatoriamente,  
**Para** evitar inconsistencias entre el estado del pedido y el dinero efectivamente cobrado.

**Criterios de Aceptación:**
1. Si la pasarela devuelve un error de timeout o estado indeterminado, el pago queda marcado como `UNCERTAIN` / `PENDING_RECONCILIATION`.
2. Un proceso de conciliación programado consulta a la pasarela utilizando la referencia de la transacción.
3. Si la conciliación confirma el cobro, el flujo de confirmación continúa; si confirma el no-cobro, la orden se cancela y se libera la reserva.

### HU-PAY-02: Reembolsos Confirmados y Reportes Financieros `[DECISIÓN APROBADA]`
**Como** administrador o auditor del sistema,  
**Quiero** que los reembolsos requieran confirmación previa del proveedor antes de completarse y se auditen por separado de las ventas,  
**Para** garantizar exactitud contable e inmutabilidad de los registros.

**Criterios de Aceptación:**
1. Un reembolso solicitado nunca se marca como completado (`REFUNDED`) hasta recibir la respuesta confirmatoria explícita del proveedor de pagos (`REFUND_CONFIRMED`).
2. El sistema genera reportes contables donde las ventas brutas confirmadas no se mutan retroactivamente; los reembolsos se reportan en una línea/métrica independiente.

---

## 7. Épica 6: Notificaciones Asíncronas

### HU-NOT-01: Notificaciones Transaccionales al Cliente `[DECISIÓN APROBADA]`
**Como** cliente de la tienda,  
**Quiero** recibir confirmaciones por correo electrónico sobre el estado de mi compra,  
**Para** estar informado sobre la recepción del pedido, cambios de estado y reembolsos.

**Criterios de Aceptación:**
1. `notification-service` escucha asíncronamente eventos de RabbitMQ (`order.confirmed`, `order.status-changed`, `payment.refund-confirmed`).
2. El servicio opera de forma desacoplada: si el servicio de notificaciones está caído o lento, no bloquea ni afecta el checkout ni los cambios de estado.
3. Los correos se simulan registrando en logs estructurados con destinatario, asunto y cuerpo para auditoría local.
