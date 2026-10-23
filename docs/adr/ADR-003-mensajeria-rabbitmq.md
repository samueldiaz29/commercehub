# ADR-003: Adopción de RabbitMQ para Mensajería Asíncrona y Eventos de Dominio

## Estado
**Aprobado** `[DECISIÓN APROBADA]`

## Fecha
2026-10-22

## Contexto
El desacoplamiento de servicios en CommerceHub (especialmente para la emisión de notificaciones, compensaciones y eventos de dominio) exige un middleware de mensajería asíncrona confiable. Se requiere:
- Publicación y suscripción con enrutamiento flexible por tópicos (Topic Exchanges).
- Soporte nativo para colas de descarte y mensajes muertos (**Dead Letter Exchanges / DLQ**).
- Bajo consumo de recursos y tiempo de arranque rápido en entornos de desarrollo local y pipelines de CI (Testcontainers).
- Integración madura y nativa con el ecosistema Spring Boot (`spring-boot-starter-amqp`).

## Decisión
Adoptar **RabbitMQ** (versión con interfaz de administración web y soporte AMQP 0-9-1) como broker de mensajería principal para la arquitectura:
1. **Topología de Exchanges:** Se utilizará un `Topic Exchange` principal (`commercehub.events`) para el enrutamiento selectivo de eventos de negocio mediante patrones de enrutamiento basados en puntos (ej. `order.*`, `payment.*`).
2. **Dead Lettering:** Se configurará un `Direct Exchange` o `Fanout` dedicado a DLQ (`commercehub.dlx`) vinculado a cada cola mediante las directivas `x-dead-letter-exchange` de RabbitMQ.
3. **Mecanismo de Publicación:** Los eventos se persisten primero en una tabla de Transactional Outbox y se publican asíncronamente con confirmaciones de entrega de publicador activadas (*Publisher Confirms*).

## Consecuencias

### Positivas
- **Enrutamiento Flexible:** Las expresiones de routing key de AMQP permiten a los consumidores suscribirse únicamente a los eventos específicos que les interesan.
- **Ligereza Operativa:** A diferencia de plataformas de streaming pesado, una instancia de RabbitMQ consume menos de 150 MB de memoria en Docker Compose y levanta en cuestión de segundos en Testcontainers.
- **Soporte Nativo de DLQ y TTL:** Facilita el manejo de reintentos fallidos y expiración temporal de mensajes sin código adicional de infraestructura.

### Negativas / Desafíos
- **Sin Replay Histórico Persistente:** RabbitMQ elimina los mensajes de la cola una vez consumidos y confirmados (ACK); no actúa como log de eventos histórico inmutable (event store) a largo plazo.
- **Gestión de Backpressure:** Si los consumidores se saturan, las colas pueden crecer en memoria requiriendo configuración de límites (`x-max-length`).

## Alternativas Consideradas y Descartadas
1. **Apache Kafka:**
   - *Por qué se descartó:* Introduciría una complejidad desproporcionada para el alcance de un comercio electrónico de un solo comercio (gestión de particiones, rebalanceos de grupos de consumidores, Zookeeper o KRaft, y consumo excesivo de memoria en Docker local). La regla del proyecto prohíbe explícitamente Kafka sin justificación de escala extrema.
2. **Redis Streams / PubSub:**
   - *Por qué se descartó:* Aunque es ligero, Redis no ofrece garantías de persistencia ni primitivas avanzadas de AMQP como Topic routing, acknowledgements transaccionales y Dead Lettering declarativo sin desarrollo a medida.
3. **AWS SQS / SNS / Azure Service Bus:**
   - *Por qué se descartó:* Servicios propietarios de nube pública que rompen la reproducibilidad local offline y requieren emuladores (como LocalStack) en el entorno de desarrollo.
