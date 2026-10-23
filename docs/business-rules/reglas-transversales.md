# Reglas de Negocio Transversales — CommerceHub

Este documento compila las políticas, invariantes y reglas de negocio del dominio de CommerceHub, diferenciando explícitamente entre **Decisiones Aprobadas**, **Propuestas Técnicas** y **Dudas o Decisiones Pendientes**.

---

## 1. Reglas de Negocio del Dominio Comercial `[DECISIONES APROBADAS]`

### RN-INV-01: Reserva Temporal de Inventario (TTL 15 Minutos)
- **Definición:** Toda solicitud de compra aceptada en el inicio del checkout bloquea el stock solicitado mediante una reserva temporal en `inventory-service`.
- **Invariante:** La reserva tiene un tiempo de vida estricto de **15 minutos** desde su creación.
- **Expiración:** Si transcurridos los 15 minutos el pago no ha sido confirmado, la reserva expira y las unidades reservadas retornan automáticamente al stock disponible para la venta.

### RN-PAY-01: Pasarela de Pagos Simulada e Intercambiable
- **Definición:** Los pagos se procesan mediante una abstracción (`PaymentProviderGateway`) que desacopla la lógica de cobro del proveedor concreto.
- **Simulación Controlada:** La implementación inicial simula las respuestas de la pasarela mediante parámetros o cabeceras de prueba, permitiendo emular:
  - Autorización exitosa.
  - Rechazo por fondos insuficientes o tarjeta inválida.
  - Timeout de red / caída de pasarela (resultado incierto).
  - Error irrecuperable de pasarela.

### RN-PAY-02: Manejo de Duplicados, Errores y Resultados Inciertos
- **Idempotencia Obligatoria:** Toda operación de cobro requiere una clave de idempotencia (`idempotency_key` provista por el cliente o generada por la Saga). Peticiones repetidas con la misma clave deben retornar el resultado idéntico previo sin generar cargos adicionales.
- **Resultado Incierto (Timeout / Ambiguo):** Si la comunicación con la pasarela falla por timeout o error no concluyente, la transacción entra en estado `UNCERTAIN` / `PENDING_RECONCILIATION`.
- **Conciliación Obligatoria:** Queda prohibido asumir el cobro como fallido o exitoso sin antes conciliar formalmente con la pasarela (mediante polling o webhook de conciliación).

### RN-PAY-03: Reembolsos Auditados y Confirmación Previa
- **Confirmación Requerida:** Un reembolso no puede marcarse internamente como completado (`REFUNDED`) hasta recibir la confirmación explícita y exitosa del proveedor de pagos (`REFUND_CONFIRMED`).
- **Inmutabilidad y Reporte Separado:** Los reembolsos se auditan de forma independiente. No se descuenta retroactivamente el monto de la venta original; la venta se mantiene registrada como cobrada y el reembolso se anota como un débito/movimiento contable en una línea/métrica separada.

### RN-ORD-01: Política de Cancelación Directa por el Cliente
- **Ventana de Cancelación:** El cliente únicamente puede cancelar su pedido de forma directa a través de la aplicación si el pedido se encuentra en estado `CONFIRMED` y **antes** de que su estado cambie a preparación (`IN_PREPARATION`).
- **Bloqueo Operativo:** Una vez que el pedido pasa a `IN_PREPARATION`, el almacén ha iniciado el empaque físico. A partir de ese momento, la cancelación directa queda estrictamente inhabilitada en la interfaz del cliente. Cualquier solicitud posterior requiere canalización administrativa manual.

### RN-ORD-02: Política de Tarifas de Envío y Moneda
- **Tarifa Plana:** Se aplica una tarifa fija de envío de **S/ 10.00 (diez soles)** por cada orden completada.
- **Configurabilidad:** El valor no debe estar codificado como número mágico (*hardcoded*), sino expuesto como parámetro de configuración centralizado en el microservicio de órdenes.
- **Moneda Base:** La moneda oficial e inicial de todo el sistema es el **Sol Peruano (PEN)**. Todos los montos se almacenan y procesan en PEN con dos decimales de precisión (`BigDecimal`).

### RN-ORD-03: Definición de Venta Confirmada
- **Condición Indispensable:** Una venta solo se computa como formalmente confirmada cuando se cumplen simultáneamente dos condiciones:
  1. El pago ha sido autorizado y capturado con éxito (`payment_status = PAID`).
  2. La reserva de inventario ha sido consolidada formalmente como deducción de stock físico (`inventory_status = CONFIRMED`).

### RN-SEC-01: Modelo de Registro Público y Privilegios
- **Auto-registro Limitado:** El registro público expuesto en el portal web asigna de forma exclusiva e irrevocable el rol de cliente (`CUSTOMER`).
- **Aprovisionamiento de Administradores:** No existe endpoint ni opción pública para registrarse como administrador (`ADMIN`). Los usuarios con dicho rol deben ser creados directamente por un administrador en la consola o vía scripts de inicialización en Keycloak.

### RN-SEC-02: Seguridad y Protección de Datos de Pago (PCI-DSS Lite)
- **Prohibición de Almacenamiento:** Está estrictamente prohibido guardar o registrar en bases de datos o logs:
  - El número primario de cuenta completo (PAN / tarjeta completa).
  - El código valor de verificación (CVV / CVC).
- **Datos Permitidos:** Únicamente se permite persistir los últimos 4 dígitos (para identificación visual del usuario), la marca de la tarjeta (Visa/Mastercard) y el token de pago provisto por la pasarela simulada.

---

## 2. Reglas de Arquitectura Distribuida e Integración `[DECISIONES APROBADAS]`

### RN-DIST-01: Autonomía de Datos y Ausencia de Transacciones ACID Globales
- Cada microservicio posee y administra su propio esquema de base de datos relacional PostgreSQL.
- Se prohíbe el acceso cruzado directo a esquemas ajenos y el uso de coordinadores de transacciones distribuidas basadas en dos fases (2PC / XA).
- La consistencia entre servicios se rige bajo el principio de **consistencia eventual**.

### RN-DIST-02: Coordinación mediante Saga Orquestada y Persistente
- El flujo integral de checkout y compra está orquestado exclusivamente por `order-service`.
- La máquina de estados de la Saga debe ser persistente en base de datos para garantizar que ante caídas o reinicios del servicio, el flujo pueda reanudarse o compensarse sin pérdida de estado.

### RN-DIST-03: Publicación Confiable mediante Transactional Outbox
- Ningún microservicio debe enviar mensajes directamente al broker de mensajería dentro de la transacción de negocio local de la base de datos.
- Toda emisión de eventos de dominio se escribe en una tabla de `outbox` en la misma transacción local del agregado. Un proceso de relay lee y publica los eventos a RabbitMQ, garantizando entrega *at-least-once*.

### RN-DIST-04: Idempotencia en Consumidores de Eventos
- Los consumidores de RabbitMQ deben ser tolerantes a mensajes duplicados. Cada mensaje debe portar un identificador único (`event_id` o `message_id`) que se registra al procesarse para evitar reprocesamiento.

---

## 3. Dudas, Contradicciones y Decisiones Pendientes `[PENDIENTE DE DECISIÓN]`

A continuación se registran las contradicciones técnicas y vacíos comerciales identificados durante el análisis arquitectónico, los cuales no han sido inventados y requieren acuerdo formal:

| ID | Tema | Descripción de la Duda / Contradicción | Impacto Técnico | Alternativas a Evaluar |
| :--- | :--- | :--- | :--- | :--- |
| **DP-01** | **Fluctuación de Precios en Carrito** | Si un producto cambia de precio en el catálogo mientras un cliente lo tiene en el carrito: ¿se debe congelar el precio en el carrito o actualizarlo automáticamente al iniciar el checkout? | Inconsistencia entre total del carrito y monto cobrado en la pasarela. | **A:** Actualizar al checkout y alertar al cliente si subió. <br>**B:** Bloquear precio por tiempo limitado (no recomendado). |
| **DP-02** | **Reingreso de Stock tras Cancelación de Venta** | Cuando el cliente cancela un pedido confirmado (antes de preparación), el dinero se reembolsa: ¿el stock deducido se devuelve automáticamente a disponible mediante un comando a `inventory-service`, o requiere validación de almacén? | Falta definir si la Saga de cancelación incluye compensación automática de inventario. | **A:** Compensación automática inmediata vía evento `OrderCancelled`. <br>**B:** Retención en cuarentena de inventario. |
| **DP-03** | **Detección del Vencimiento de Reserva (TTL 15 min)** | ¿Quién es el responsable cronológico de detectar los 15 minutos de la reserva? ¿Un proceso programado (*scheduler*) en `inventory-service` que publica `InventoryReservationExpired`, o un temporizador en `order-service`? | Duplicidad de temporizadores o desincronización horaria entre servicios. | **A:** Scheduler en `inventory-service` que expira registros vencidos y emite evento. <br>**B:** RabbitMQ Dead-Letter / TTL message. |
| **DP-04** | **Canal de Comunicación de la Saga (HTTP vs RabbitMQ)** | Para la Saga orquestada por `order-service`: ¿las llamadas de reserva a `inventory-service` y cobro a `payment-service` son REST síncronas con reintentos/circuit breaker, o comandos asíncronos mediante colas dedicadas de RabbitMQ? | Complejidad de implementación y latencia percibida por el usuario en el checkout. | **A:** Llamadas REST internas síncronas con timeout estricto + Outbox para eventos. <br>**B:** Comandos 100% asíncronos sobre RabbitMQ. |
| **DP-05** | **Política de Envío ante Cancelaciones Parciales** | Dado que el costo de envío es fijo (S/ 10.00): ¿se permiten cancelaciones parciales de líneas de pedido por parte del cliente o administrador, y cómo afecta al costo de envío y reembolso? | Complejidad de la máquina de estados del pedido. | **Propuesta inicial recomendada:** Sólo permitir cancelación total del pedido por parte del cliente. |
