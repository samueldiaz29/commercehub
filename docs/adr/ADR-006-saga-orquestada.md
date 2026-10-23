# ADR-006: Coordinación de Transacciones Distribuidas mediante Saga Orquestada y Transactional Outbox

## Estado
**Aprobado** `[DECISIÓN APROBADA]`

## Fecha
2026-10-22

## Contexto
El proceso de checkout en CommerceHub involucra múltiples microservicios con almacenes de datos aislados:
1. `order-service` registra el pedido.
2. `inventory-service` bloquea temporalmente las unidades (15 minutos).
3. `payment-service` autoriza y captura el cobro con idempotencia.
4. `inventory-service` confirma la deducción definitiva del stock.
5. `notification-service` notifica al cliente.

Dado que cada servicio tiene su propia base de datos PostgreSQL, no existe una transacción ACID global. Se requiere un mecanismo de consistencia eventual que garantice:
- Que si el pago falla, la reserva de inventario sea liberada inmediatamente (compensación).
- Que si la reserva expira a los 15 minutos, el pedido sea cancelado.
- Que ante caídas del servidor o reintentos de red, el estado de la transacción sea recuperable y no genere dobles cobros o desincronizaciones de stock.
- Que la publicación de eventos no sufra inconsistencias por fallos de doble escritura (*dual-write problem*).

## Decisión
Adoptar una **Saga Orquestada y Persistente** coordinada por **`order-service`**, complementada con el patrón **Transactional Outbox**:
1. **Orquestador Central:** `order-service` mantiene el control del flujo del checkout, dictando cuándo reservar, cuándo cobrar y cuándo confirmar o compensar.
2. **Persistencia de la Saga:** El estado de avance de la transacción se persiste en la tabla `saga_instances` de `order_db` en cada transición de paso, permitiendo a un worker de recuperación retomar o compensar compras pendientes tras caídas del sistema.
3. **Compensaciones Explícitas:**
   - Si el cobro es rechazado $\to$ se emite comando de liberación de reserva (`Release Reservation`) y se marca el pedido como `REJECTED`.
   - Si el pago queda en estado incierto $\to$ se detiene la compensación destructiva y se activa conciliación obligatoria antes de proceder.
4. **Transactional Outbox:** Todo evento emitido hacia RabbitMQ (ej. `order.confirmed`) se guarda en la tabla `outbox_events` dentro de la misma transacción SQL local del agregado, siendo publicado por un worker asíncrono con acuse de recibo.

## Consecuencias

### Positivas
- **Visibilidad Centralizada:** Es posible saber en todo momento el estado exacto de una compra consultando la tabla de saga en `order-service`, facilitando la depuración y auditoría.
- **Sin Dependencias Cíclicas:** Los servicios participantes (`inventory-service`, `payment-service`) no necesitan conocer la lógica global del checkout ni suscribirse a eventos de otros participantes.
- **Tolerancia a Caídas y Reanudación:** Si el servicio de órdenes se reinicia a mitad de un checkout, el estado persistido en PostgreSQL permite reanudar o compensar la transacción de forma segura.
- **Fiabilidad en Publicación:** El patrón Outbox previene la pérdida de mensajes ante caídas de conexión con RabbitMQ.

### Negativas / Desafíos
- **Punto Central de Orquestación:** `order-service` asume una mayor responsabilidad lógica y carga de coordinación.
- **Sobrecarga de Persistencia:** Requiere escrituras adicionales en la base de datos para registrar cada paso de la saga y las filas de outbox.

## Alternativas Consideradas y Descartadas
1. **Saga Coreografiada (Choreography-based Saga):**
   - *Por qué se descartó:* En una saga coreografiada los servicios reaccionan a eventos mutuos sin un orquestador. Esto dispersa la lógica del flujo de checkout entre múltiples repositorios, dificulta la trazabilidad operativa ("¿dónde se quedó mi pedido?") y crea riesgo de bucles de eventos y acoplamiento indirecto.
2. **Transacciones Distribuidas 2PC / XA (Two-Phase Commit):**
   - *Por qué se descartó:* Introduce bloqueos prolongados en las tablas de base de datos mientras se espera la confirmación de la red, genera un punto único de fallo catastrófico en el coordinador y no es compatible con entornos cloud modernos ni microservicios desacoplados.
