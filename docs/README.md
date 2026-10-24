# CommerceHub — Documentación de Arquitectura y Dominio

Bienvenido a la documentación técnica y funcional de **CommerceHub**, una plataforma de comercio electrónico monocomercio (single-merchant) diseñada como proyecto de portafolio profesional de alta calidad, siguiendo principios de arquitectura de microservicios, código limpio, pruebas rigurosas y alta mantenibilidad.

---

## 1. Guía Obligatoria para Futuros Desarrolladores y Agentes de IA

> [!IMPORTANT]
> **Norma de Gobernanza para Agentes y Desarrolladores:**
> Todo cambio en la arquitectura, adición de dependencias, definición de endpoints o modificación de flujos de negocio DEBE ceñirse a las decisiones consolidadas en este repositorio. Queda terminantemente prohibido alterar el stack aprobado o asumir políticas comerciales sin validación explícita.

### Principios Rectores
1. **Stack Intocable:** La versión base del lenguaje es **Java 25**. No degradar a versiones previas (Java 21, 17, etc.). El framework principal es **Spring Boot** con Spring MVC en servicios de negocio y Spring Cloud Gateway en el borde.
2. **Infraestructura Aprobada:** La persistencia relacional utiliza **PostgreSQL** (con compatibilidad Neon para despliegue serverless administrado). La mensajería asíncrona utiliza **RabbitMQ**. El entorno local se gestiona con **Docker Compose**.
   - *Restricción explícita:* No incorporar Kafka, Redis, Elasticsearch ni Kubernetes salvo justificación técnica formal y aprobación expresa.
3. **Autonomía de Datos (Database-per-Service):** Cada microservicio es el único dueño de su esquema de persistencia. Queda prohibido el acceso directo a bases de datos de otros servicios o la ejecución de transacciones distribuidas 2PC/XA.
4. **Consistencia Eventual y Sagas:** La coordinación entre servicios en operaciones de negocio mutables (como el Checkout) se realiza mediante **Sagas Orquestadas y Persistentes** gestionadas por `order-service`, respaldadas por el patrón **Transactional Outbox**.
5. **Idempotencia Obligatoria:** Todos los endpoints mutables críticos (pagos, checkout) y todos los consumidores de eventos/comandos deben ser idempotentes para tolerar reintentos de red sin efectos colaterales.
6. **Seguridad y Privacidad Financiera (PCI-DSS Lite):** No se almacenan números de tarjetas completos (PAN) ni códigos de seguridad (CVV). La autenticación y autorización se delega a **Keycloak** vía OIDC/OAuth2.

---

## 2. Mapa de Navegación de la Documentación

La documentación se organiza en las siguientes carpetas y documentos especializados:

```
docs/
├── README.md                                  # Índice general y guía de gobernanza (este archivo)
├── conventions/
│   └── convenciones-de-desarrollo.md          # Estándares de desarrollo, nomenclatura y git
├── requirements/
│   └── historias-de-usuario.md                # Historias de usuario, criterios de aceptación y épicas
├── business-rules/
│   ├── reglas-transversales.md                # Reglas de negocio globales, políticas e invariantes
│   └── estados-y-transiciones.md              # Máquinas de estado: Pedido, Pago, Reserva, Reembolso
├── architecture/
│   ├── arquitectura.md                        # Vista C4, límites de contexto y persistencia implementada del catálogo
│   └── saga-checkout.md                       # Especificación detallada de la Saga de Checkout
├── api/
│   ├── contratos.md                           # Especificación de contratos REST, headers y códigos HTTP
│   └── eventos.md                             # Topología de eventos RabbitMQ, routing keys y esquemas
└── adr/                                       # Architectural Decision Records (ADRs)
    ├── ADR-001-arquitectura-microservicios.md # Adopción de microservicios
    ├── ADR-002-identidad-keycloak.md          # Gestión de identidad y RBAC
    ├── ADR-003-mensajeria-rabbitmq.md         # Broker de mensajería asíncrona
    ├── ADR-004-persistencia-postgresql.md     # Base de datos relacional por servicio
    ├── ADR-005-spring-mvc.md                  # Modelo de programación Spring MVC + Cloud Gateway
    └── ADR-006-saga-orquestada.md             # Orquestación de Saga con Transactional Outbox
```

---

## 3. Resumen del Stack Tecnológico Aprobado

| Componente | Tecnología Seleccionada | Justificación / Propósito |
| :--- | :--- | :--- |
| **Lenguaje de Programación** | Java 25 | Rendimiento moderno, Virtual Threads, Pattern Matching y soporte a largo plazo. |
| **Framework Base** | Spring Boot | Ecosistema empresarial maduro, integración nativa de dependencias. |
| **API Gateway** | Spring Cloud Gateway | Enrutamiento perimetral, gestión reactiva de tráfico de entrada y CORS. |
| **Servicios de Negocio** | Spring MVC + Spring Data JPA / Hibernate | Modelo imperativo claro, transaccionalidad local robusta y mapeo ORM. |
| **Reducción de código Java** | Lombok (por módulo) | Procesamiento de anotaciones en compilación; actualmente limitado a `catalog-service`. |
| **Persistencia** | PostgreSQL / Neon | Integridad referencial ACID por servicio, soporte JSONB y escalabilidad. |
| **Migración de Esquemas** | Flyway | Versionado reproducible de DDL en cada base de datos. |
| **Mensajería Asíncrona** | RabbitMQ | Enrutamiento flexible AMQP (Direct/Topic), Dead Lettering y bajo consumo de recursos. |
| **Gestión de Identidad (IAM)** | Keycloak (OIDC / OAuth2) | Centralización de usuarios, emisión de JWT y RBAC desacoplado. |
| **Testing** | JUnit 5, Mockito, Testcontainers | Pruebas unitarias e integración con PostgreSQL real en `catalog-service`; estrategia aplicable a los demás servicios. |
| **Frontend** | React + TypeScript | SPA desacoplada consumiendo las APIs a través del Gateway. |
| **Automatización y Despliegue** | Docker Compose + GitHub Actions | Orquestación local multicontenedor e integración continua (CI/CD). |

---

## 4. Convenciones de Estado en la Documentación

Para evitar ambigüedades durante el desarrollo, todo requisito, contrato o diagrama se etiqueta bajo la siguiente nomenclatura:

- `[DECISIÓN APROBADA]`: Requisito o directriz firme acordada por los arquitectos y el negocio. Debe implementarse tal como está descrita.
- `[PROPUESTA PENDIENTE DE VALIDACIÓN]`: Especificación técnica sugerida (ej. rutas de endpoints o nombres de colas) que requiere validación final antes de la fase de implementación.
- `[DUDA / PENDIENTE DE DECISIÓN]`: Contradicción identificada o vacío de política comercial que requiere resolución formal antes de codificar la lógica asociada.
