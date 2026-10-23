# ADR-005: Modelo de Programación con Spring MVC en Servicios de Negocio y Spring Cloud Gateway en el Borde

## Estado
**Aprobado** `[DECISIÓN APROBADA]`

## Fecha
2026-10-22

## Contexto
En arquitecturas Spring Boot modernas conviven dos paradigmas principales:
1. El modelo imperativo clásico basado en hilos bloqueantes (**Spring MVC** con Spring Data JPA).
2. El modelo reactivo no bloqueante (**Spring WebFlux** con R2DBC).

Con la llegada de **Java 25**, los hilos virtuales (*Virtual Threads* de Project Loom) transforman radicalmente la economía del modelo imperativo: permiten que un hilo bloqueante por llamada a base de datos o API tenga un costo insignificante de memoria y contexto, ofreciendo un rendimiento masivo de concurrencia sin obligar al desarrollador a escribir código funcional reactivo (`Mono`/`Flux`).

Por su parte, el API Gateway actúa como punto perimetral de alto tráfico donde el enrutamiento y la inspección de cabeceras se benefician del modelo reactivo no bloqueante.

## Decisión
1. **Servicios de Negocio (`catalog`, `inventory`, `order`, `payment`):** Utilizar **Spring MVC** junto con **Spring Data JPA e Hibernate**, habilitando el soporte nativo de **Virtual Threads de Java 25** (`spring.threads.virtual.enabled=true`).
2. **API Gateway (`api-gateway`):** Utilizar **Spring Cloud Gateway** (basado en Spring WebFlux / Reactor Netty) exclusivamente en el perímetro para gestionar el enrutamiento de peticiones, CORS y filtrado de cabeceras.

## Consecuencias

### Positivas
- **Simplicidad y Legibilidad del Código:** El modelo imperativo de Spring MVC permite código de negocio lineal, fácil de leer, probar y depurar, con stack traces claros.
- **Ecosistema Maduro:** Acceso completo a las capacidades consolidadas de Spring Data JPA, Hibernate, validaciones de Jakarta (`@Valid`), y herramientas de auditoría.
- **Concurrencia Masiva vía Virtual Threads:** Al habilitar los Virtual Threads de Java 25 en Spring MVC, el servidor de aplicaciones puede atender decenas de miles de solicitudes simultáneas sin agotar el pool de hilos de la plataforma ni requerir programación reactiva en la base de datos.
- **Rendimiento Perimetral:** Spring Cloud Gateway maximiza el *throughput* en la capa de borde sin bloquear hilos del sistema operativo mientras enruta tráfico hacia los servicios internos.

### Negativas / Desafíos
- **Dualidad de Paradigmas:** El equipo debe conocer Spring Cloud Gateway (reactivo) para la capa de entrada y Spring MVC (imperativo) para los microservicios de negocio.
- **Precaución con Bloqueo de Portadores (Carrier Thread Pinning):** En Hibernate/JPA se debe cuidar no utilizar bloques sincronizados (`synchronized`) obsoletos que aten un hilo virtual a un hilo de plataforma en operaciones de I/O pesadas (mitigado sustancialmente en Java 25).

## Alternativas Consideradas y Descartadas
1. **Adopción de WebFlux y R2DBC en Todos los Microservicios:**
   - *Por qué se descartó:* R2DBC no ofrece un ORM completo equiparable a Hibernate, carece de soporte maduro para mapeos complejos de agregados de dominio y carga de grafos, y la curva de aprendizaje de Project Reactor no se justifica ante la presencia de Virtual Threads en Java 25.
2. **Spring MVC en el API Gateway (Spring Cloud Gateway Server MVC):**
   - *Por qué se descartó:* La versión reactiva de Spring Cloud Gateway es la más probada, documentada y estandarizada en la industria para enrutamiento perimetral dinámico con filtros pre/post y compatibilidad con Keycloak.
