# ADR-001: Adopción de Arquitectura de Microservicios Desacoplados

## Estado
**Aprobado** `[DECISIÓN APROBADA]`

## Fecha
2026-10-22

## Contexto
CommerceHub es una plataforma de comercio electrónico monocomercio concebida como proyecto de portafolio profesional de alto calibre técnico. El sistema debe demostrar patrones de grado de producción para escalar de forma independiente dominios con características operativas disímiles:
- El catálogo tiene un patrón de lectura intensiva.
- El inventario exige control de concurrencia y bloqueos temporales de stock.
- La gestión de pedidos requiere orquestación de transacciones distribuidas.
- Los pagos demandan altos estándares de seguridad y aislamiento PCI-DSS Lite.
- Las notificaciones son procesos en segundo plano tolerantes a latencia.

Un diseño monolítico tradicional dificultaría el aislamiento de fallas, complicaría la demostración de patrones avanzados de consistencia eventual (como Sagas y Outbox) y acoplaría el ciclo de vida de componentes con diferentes demandas de escalabilidad.

## Decisión
Adoptar una arquitectura de **microservicios distribuidos** organizados en torno a límites de contexto (Bounded Contexts) bien definidos:
1. `api-gateway`: Enrutamiento y seguridad perimetral.
2. `catalog-service`: Gestión de productos y precios publicados en PEN.
3. `inventory-service`: Gestión de stock, reservas temporales (15 min) y movimientos de inventario.
4. `order-service`: Carrito de compras, ciclo de vida del pedido y orquestación de la Saga de checkout.
5. `payment-service`: Pagos simulados, conciliación, reembolsos y tokens de pago.
6. `notification-service`: Notificaciones asíncronas por eventos.

Cada servicio posee autonomía tecnológica dentro de las restricciones del stack (Java 25 y Spring Boot), y es el único dueño de su persistencia.

## Consecuencias

### Positivas
- **Aislamiento de Fallos:** La caída temporal de servicios secundarios (como `notification-service`) no bloquea el proceso crítico de checkout.
- **Escalabilidad Selectiva:** Capacidad de escalar réplicas de `catalog-service` o `order-service` de forma independiente según la carga de tráfico.
- **Claridad de Dominio:** Modelo de dominio enfocado, con bases de código mantenibles y acotadas.
- **Valor Pedagógico y de Portafolio:** Demuestra dominio riguroso de patrones de arquitectura distribuida corporativa.

### Negativas / Desafíos
- **Mayor Complejidad Operativa:** Requiere gestionar múltiples procesos en ejecución, redes virtuales y despliegues coordinados vía Docker Compose.
- **Complejidad de Consistencia:** Desaparición de transacciones ACID globales; requiere implementar Sagas y consistencia eventual.
- **Latencia de Red:** Mayor sobrecarga por comunicación interservicios a través de HTTP/AMQP comparado con llamadas en memoria.

## Alternativas Consideradas y Descartadas
1. **Monolito Modular (Spring Modulith):**
   - *Por qué se descartó:* Aunque reduce la sobrecarga operativa, no permite demostrar de forma transparente el aislamiento físico de bases de datos, la resiliencia ante caídas de red ni la integración de mensajería AMQP entre procesos heterogéneos en un portafolio de arquitectura distribuida.
2. **Arquitectura Serverless Pura (ej. AWS Lambda):**
   - *Por qué se descartó:* Introduce Vendor Lock-in a un proveedor cloud específico, dificulta la reproducibilidad local offline con Docker Compose y no aprovecha el ecosistema unificado de Spring Boot y Java 25.
