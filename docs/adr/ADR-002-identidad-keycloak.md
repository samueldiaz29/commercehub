# ADR-002: Gestión de Identidad y Control de Acceso con Keycloak (OIDC / OAuth2)

## Estado
**Aprobado** `[DECISIÓN APROBADA]`

## Fecha
2026-10-22

## Contexto
CommerceHub requiere un esquema robusto de autenticación y autorización que permita:
- Proteger los endpoints de la API diferenciando permisos entre Clientes (`CUSTOMER`) y Administradores (`ADMIN`).
- Garantizar que el auto-registro público únicamente conceda el rol `CUSTOMER`, evitando escalada de privilegios hacia funciones de catálogo, stock o reportes.
- Proporcionar tokens de acceso seguros a la aplicación cliente (SPA React) mediante estándares abiertos de la industria (OAuth 2.0 y OpenID Connect).
- Desacoplar a los microservicios de la lógica de almacenamiento de contraseñas, hashing, reseteo de claves y expiración de sesiones.

## Decisión
Adoptar **Keycloak** como servidor de identidad y gestión de accesos (IAM) centralizado, integrándolo mediante OIDC / OAuth 2.0:
1. **Flujo de Autenticación:** Se utiliza el flujo de código de autorización con clave de prueba para intercambio de código (*Authorization Code Flow with PKCE*) para la SPA frontend.
2. **Validación de Tokens:** El API Gateway valida los tokens JWT emitidos por Keycloak y propaga la identidad autenticada mediante cabeceras HTTP hacia los microservicios aguas abajo. Los microservicios de negocio validan las firmas y evalúan los roles asignados (`@PreAuthorize("hasRole('ADMIN')")`).
3. **Restricción de Registro:** El realm de Keycloak se configura para que los nuevos usuarios registrados públicamente adquieran de forma estricta y por defecto únicamente el rol `CUSTOMER`. El rol `ADMIN` solo puede ser otorgado por un superadministrador desde la consola interna o mediante scripts de bootstrap.

## Consecuencias

### Positivas
- **Estándar de la Industria:** Cumplimiento nativo de OAuth 2.0 y OpenID Connect (OIDC) sin reinventar la rueda criptográfica ni el almacenamiento de credenciales.
- **Seguridad Centralizada:** Políticas de contraseñas, rotación de llaves públicas (JWKS) y revocación de sesiones administradas en un solo punto.
- **Desacoplamiento:** Los microservicios de negocio operan de forma *stateless*, confiando en la firma criptográfica del JWT.

### Negativas / Desafíos
- **Consumo de Memoria:** Keycloak (basado en Quarkus) consume recursos considerables de CPU y memoria en el entorno local de desarrollo (Docker Compose).
- **Curva de Aprendizaje:** Requiere configurar y mantener la exportación/importación del realm (JSON) para que el entorno local arranque de forma determinista y sin configuración manual repetitiva.

## Alternativas Consideradas y Descartadas
1. **Autenticación Artesanal con Base de Datos Propia y Spring Security:**
   - *Por qué se descartó:* Crear tablas de usuarios y endpoints propios de `/login` y `/register` genera riesgos de seguridad (vulnerabilidades de almacenamiento de claves, tokens opacos no estándar, falta de PKCE) y constituye una mala práctica en arquitecturas empresariales modernas.
2. **SaaS Gestionado Externo (Auth0 / Okta / Firebase Auth):**
   - *Por qué se descartó:* Requiere conexión permanente a internet para desarrollo local y pruebas automatizadas, introduce límites de cuota gratuita y dificulta la ejecución hermética en pipelines de CI con Testcontainers.
