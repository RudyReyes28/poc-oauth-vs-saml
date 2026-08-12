# poc-oauth-vs-saml
# OAuth vs SAML: delegación de identidad

Investigación y POC comparando los dos estándares más usados para delegar autenticación a un tercero, implementados sobre Spring Boot con dos proveedores OAuth/OIDC (GitHub y Google) y un proveedor SAML (Auth0).

## 1. Delegación de identidad y Single Sign-On

Delegar identidad es que una aplicación confíe en que un tercero ya verificó quién es el usuario, en vez de verificarlo ella misma. La aplicación nunca toca la contraseña real - solo recibe una prueba de que la verificación ya ocurrió en otro lado.

Single Sign-On es la consecuencia práctica: si varias aplicaciones confían en el mismo proveedor, el usuario se autentica una sola vez ante ese proveedor y todas lo reconocen sin pedirle credenciales de nuevo.

**La problemática que resuelven:** sin delegación, cada aplicación tendría que pedir la contraseña directamente (con el riesgo de manejarla mal) y mantener su propia base de datos de usuarios, duplicando el trabajo de seguridad en cada sistema. La delegación centraliza esa responsabilidad en un proveedor especializado.

**Ecosistema de cada estándar:** OAuth 2.0 + OIDC nació en el mundo web/API moderno - apps móviles, SPAs, APIs REST. Es lo que hay detrás de "Login with Google/GitHub". SAML 2.0 nació en el mundo empresarial, más viejo (2005 vs. 2012 de OAuth), y es el estándar típico de SSO corporativo - sistemas internos, Salesforce, Workday, etc.

### Cómo se implementó

El proyecto quedó dividido en `backend-oauth` (GitHub + Google) y `backend-saml` (Auth0), cada uno usando el mecanismo nativo que trae Spring Security para su protocolo - `oauth2Login()` en un caso, `saml2Login()` en el otro. No se construyó nada del protocolo desde cero; el trabajo real estuvo en la configuración de cada proveedor y en conectar el resultado con el sistema de roles local.

## 2. OAuth 2.0 como protocolo de delegación

OAuth 2.0, en su diseño original, no es un protocolo de autenticación - es un protocolo de autorización delegada. Responde "¿le doy permiso a esta app de acceder a este recurso mío, con estos permisos específicos?", no "¿quién eres?". El caso típico es una app pidiendo acceso limitado a un recurso en otro servicio (leer archivos de Drive, por ejemplo), sin que esa app llegue a ver la contraseña del dueño del recurso.

### Cómo se implementó

`backend-oauth` usa GitHub precisamente para demostrar este caso puro. Con `spring-boot-starter-oauth2-client` y el registro correspondiente, Spring expone automáticamente `/oauth2/authorization/github`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ...
            client-secret: ...
```

Con GitHub, lo que llega después del login son solo los `attributes` planos de su API REST (`login`, `id`, `avatar_url`) - no hay ningún token de identidad estructurado, porque GitHub no implementa la capa de autenticación estandarizada que sí trae OIDC.

## 3. OIDC como capa de autenticación

OpenID Connect se construyó encima de OAuth 2.0 porque, en la práctica, la industria empezó a usar OAuth como si fuera autenticación sin que lo fuera. OIDC estandariza ese uso agregando el `ID Token`, un JWT firmado que sí responde "¿quién eres?", con claims de identidad (`sub`, `email`, `name`, `iss`, `exp`).

### Cómo se implementó

Google sí implementa OIDC completo, así que se usó como el segundo proveedor para tener el contraste directo con GitHub, en el mismo controller:

```java
boolean esOidc = principal instanceof OidcUser;

if (esOidc) {
    OidcUser oidcUser = (OidcUser) principal;
    model.addAttribute("idToken", oidcUser.getIdToken().getTokenValue());
}
```

`OidcUser` es una subinterfaz de `OAuth2User` que Spring solo construye cuando el proveedor habla OIDC. El `id_token` resultante se decodificó pegándolo en jwt.io - mismo ejercicio que se hizo con los JWT propios de la práctica de Session vs JWT, confirmando que el payload es legible sin la clave secreta, solo firmado, no cifrado.

## 4. Seguridad en OAuth

El control principal de seguridad en OAuth son los *scopes* - el mecanismo para limitar exactamente qué puede hacer el cliente con el recurso delegado, minimizando el daño si el token se filtra. Otro punto relevante es la validación de `redirect_uri`: el proveedor solo redirige a URLs pre-registradas, evitando que un atacante desvíe el token a un sitio malicioso.

### Cómo se implementó

En este POC no se pidieron scopes de recursos externos (no se accedió a Drive ni a repos de GitHub), solo los scopes mínimos de identidad (`openid email profile` para Google). La validación de `redirect_uri` se ve reflejada en la configuración de cada proveedor - tanto GitHub como Google exigieron registrar exactamente `http://localhost:8084/login/oauth2/code/{provider}` como callback autorizado; cualquier otra URL es rechazada por el proveedor antes de que la aplicación reciba nada.

## 5. SAML como estándar empresarial

SAML es un protocolo basado en XML para el intercambio de identidad entre organizaciones, con dos roles definidos: el **Identity Provider (IdP)**, quien autentica, y el **Service Provider (SP)**, la aplicación a la que se quiere entrar. Es más verboso que OAuth/OIDC (XML contra JSON), pero sigue siendo dominante en software empresarial que lo soporta de forma nativa desde hace años.

### Cómo se implementó

`backend-saml` juega el rol de Service Provider, usando `spring-security-saml2-service-provider`. Como Identity Provider se usó Auth0, configurado con su Addon "SAML2 Web App" - Auth0 no es SAML por defecto (usa OAuth/OIDC como Google), pero expone ese protocolo como una capa adicional habilitable, lo cual en sí mismo es una demostración práctica de que un mismo proveedor puede hablar varios estándares según cómo se configure.

## 6. SAML Assertion

Es el equivalente conceptual al JWT de OIDC - el paquete de prueba que el IdP entrega al SP, firmado digitalmente, confirmando la identidad y los atributos del usuario. La diferencia de forma es notable: mientras un JWT es compacto en Base64, una SAML Assertion es un documento XML completo, con espacios de nombres y estructura mucho más verbosa.

### Cómo se implementó

Los atributos llegan ya extraídos por Spring Security en un `Saml2AuthenticatedPrincipal`, sin que el código tenga que parsear XML manualmente:

```java
if (authentication.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal) {
    model.addAttribute("name", principal.getName());
    model.addAttribute("attributes", principal.getAttributes());
}
```

Los atributos que llegan desde Auth0 (email, given name, surname, name identifier) corresponden exactamente a lo declarado en el metadata del IdP bajo `<Attribute Name="...">`, confirmando que Spring está mapeando la *assertion* correctamente contra lo que el IdP publicó.

## 7. Metadata para el intercambio de configuración

Antes de que IdP y SP puedan confiar el uno en el otro, necesitan intercambiar un documento de metadata: URLs de los endpoints de cada uno, certificados públicos para verificar firmas, y qué formatos de identificador soportan.

### Cómo se implementó

El SP genera su propio metadata automáticamente en `/saml2/service-provider-metadata/auth0`, sin necesidad de escribirlo a mano - Spring lo arma a partir del `entity-id` y el certificado configurados. Del otro lado, el metadata del IdP se obtuvo de Auth0 vía su Addon SAML2, configurando `assertingparty.metadata-uri` apuntando directo a la URL pública de Auth0:

```properties
spring.security.saml2.relyingparty.registration.auth0.entity-id={baseUrl}/saml2/service-provider-metadata/{registrationId}
spring.security.saml2.relyingparty.registration.auth0.signing.credentials[0].private-key-location=classpath:saml-private-key.pem
spring.security.saml2.relyingparty.registration.auth0.signing.credentials[0].certificate-location=classpath:saml-certificate.pem
spring.security.saml2.relyingparty.registration.auth0.assertingparty.metadata-uri=https://dev-lr4tczvujf0vy7gl.us.auth0.com/samlp/metadata/vCWjx6WsNpV6V2I0kmObjP2f7Evf5Req
```

Se optó por propiedades planas (`.properties`) en vez de YAML para esta sección específica, después de que la configuración anidada en `.yml` fallara repetidamente con el error `relyingPartyRegistrationRepository cannot be null` - un problema de binding de propiedades, no de la configuración de SAML en sí. El formato plano elimina cualquier ambigüedad de indentación.

También se generó un certificado propio para el SP con `keytool`, exportado a formato PEM - necesario porque Spring Security SAML2 firma las peticiones salientes con esa credencial, y sin ella el `RelyingPartyRegistration` no puede construirse.

## 8. Seguridad en SAML

Las *assertions* van firmadas digitalmente (XML Signature), y a veces también cifradas. Son vulnerables a ataques de tipo XML Signature Wrapping si la validación de la firma no es cuidadosa, y normalmente incluyen una ventana de validez corta (`NotBefore`/`NotOnOrAfter`) para limitar el tiempo en que una *assertion* interceptada podría reutilizarse.

### Cómo se implementó

La verificación de firma la maneja Spring Security internamente usando el certificado publicado en el metadata del IdP (el `X509Certificate` dentro del `KeyDescriptor` que Auth0 expone). No se implementó validación manual de firma - es exactamente el tipo de lógica criptográfica que no debería reimplementarse a mano, y usar la librería estándar de Spring en vez de código propio es la decisión de seguridad correcta aquí.

## 9. Autorización local: por qué la delegación de identidad no delega roles

Ni OAuth ni SAML, en este POC, delegan la decisión de qué puede hacer el usuario dentro del sistema - eso sigue siendo responsabilidad exclusiva de la aplicación. Es una distinción real de la industria: con proveedores públicos de bajo trust (Google, GitHub), todo usuario nuevo entra con el rol más bajo por defecto, y la promoción a un rol mayor pasa por un canal aparte, nunca por el propio login. Con proveedores empresariales de alto trust (Azure AD, Okta), sí es común delegar también la autorización vía claims o atributos de grupo - pero ese no es el escenario cubierto en este POC.

### Cómo se implementó

Se aplicó JIT provisioning (Just-In-Time provisioning): la primera vez que alguien entra por cualquier proveedor, se crea un registro local en la tabla `app_users` con rol `USER` fijo:

```java
private AppUser buscarOCrearUsuario(String email, String provider) {
    return appUserRepository.findByEmail(email)
        .orElseGet(() -> appUserRepository.save(
            AppUser.builder()
                .email(email)
                .provider(provider)
                .enabled(true)
                .role(Role.USER)
                .build()
        ));
}
```

Esto corre tanto en `CustomOAuth2UserService` (GitHub) como en `CustomOidcUserService` (Google), interceptando el flujo de Spring Security antes de completar el login. El `AdminController`, protegido con `@PreAuthorize("hasRole('ADMIN')")`, permite promover manualmente a un usuario ya existente - la autorización sigue siendo 100% del sistema propio, sin importar qué proveedor confirmó la identidad.

## 10. Funcionalidades, beneficios, complejidades y escenarios de cada método

Esta sección responde directamente al entregable de la práctica, resaltando estos cuatro puntos para cada método.

### OAuth 2.0 + OIDC

**Funcionalidades demostradas en el POC:**
- Login delegado a un proveedor externo sin que la aplicación toque la contraseña (GitHub y Google).
- Distinción en código entre un proveedor OAuth puro (GitHub, solo `attributes`) y uno OIDC (Google, con `id_token` firmado).
- JIT provisioning: creación automática del usuario local en el primer login, con rol por defecto.
- Autorización propia sobre ese usuario local vía `@PreAuthorize("hasRole('ADMIN')")`.

**Beneficios:**
- Configuración inicial rápida - un client-id y un client-secret alcanzan para tener el flujo funcionando.
- Formato de token compacto (JWT), fácil de transportar en un header HTTP y de decodificar para depurar.
- Ecosistema amplio: prácticamente cualquier servicio web moderno expone un client OAuth/OIDC listo para usar.
- Encaja de forma natural con arquitecturas de API REST y SPA, donde SAML resulta más pesado de integrar.

**Complejidades:**
- OAuth por sí solo no resuelve autenticación - es fácil implementarlo mal si no se entiende que hace falta OIDC encima para tener identidad real.
- El manejo de *scopes* exige disciplina: pedir de más amplía la superficie de riesgo si el token se filtra.
- La revocación de acceso depende de cada proveedor; no hay un estándar único para "cerrar sesión en todos lados" tan uniforme como en SAML con Single Logout.

**Escenarios típicos de uso:**
- "Login with Google/GitHub/Facebook" en aplicaciones de consumo.
- Apps móviles o SPAs que consumen una API propia y necesitan identidad de terceros.
- Integraciones donde una aplicación necesita acceso limitado a un recurso de otro servicio (calendario, almacenamiento, repositorios) en nombre del usuario.

### SAML 2.0

**Funcionalidades demostradas en el POC:**
- Flujo SP-Initiated SSO completo contra un IdP real (Auth0, con su Addon SAML2 Web App).
- Generación automática del metadata del SP y consumo del metadata del IdP para el intercambio de configuración.
- Extracción de atributos de la *assertion* (email, nombre, apellido) mapeados directo a un `Saml2AuthenticatedPrincipal`.
- Logout con invalidación de la sesión local.

**Beneficios:**
- Estándar maduro y ampliamente soportado por software empresarial (Salesforce, Workday, sistemas de RRHH, etc.), sin necesidad de que cada proveedor construya su propia integración custom.
- Las *assertions* pueden llevar atributos ricos (rol, departamento, grupo), habilitando escenarios de autorización delegada cuando el IdP es de alto trust.
- Single Logout estandarizado entre SP e IdP, algo que OAuth/OIDC no define de forma tan uniforme.

**Complejidades:**
- Configuración inicial notablemente más pesada: certificados propios, intercambio de metadata, y en este POC, varios intentos fallidos por servicios de prueba caídos (samltest.id) antes de llegar a una configuración estable con Auth0.
- Los mensajes de error son menos claros que en OAuth/OIDC - problemas de binding de propiedades o de certificados no siempre indican la causa real de forma directa.
- El formato XML es más pesado de transportar y de depurar a simple vista comparado con un JWT compacto.

**Escenarios típicos de uso:**
- SSO corporativo interno, donde el mismo directorio de la empresa (Active Directory, Okta, etc.) es la fuente de verdad para múltiples sistemas internos.
- Integraciones B2B donde una empresa necesita que sus empleados accedan a un sistema de un tercero (ej. una plataforma de RRHH externa) sin crear cuentas nuevas.
- Entornos regulados o legados donde el software ya soporta SAML nativamente y no vale la pena migrar a OAuth/OIDC.

## Resumen comparativo

| Dimensión | OAuth 2.0 + OIDC | SAML 2.0 |
|---|---|---|
| Qué delega por diseño | Autorización sobre un recurso (OAuth) + identidad (OIDC, capa adicional) | Identidad, con posibilidad de atributos/roles |
| Formato del token/assertion | JWT - compacto, Base64 | XML - verboso, con namespaces |
| Ecosistema típico | Apps web modernas, móviles, APIs REST | Sistemas empresariales, SSO corporativo |
| Complejidad de configuración inicial | Baja - registro de client-id/secret, poca config | Alta - certificados, intercambio de metadata, mappers |
| Autorización dentro del sistema propio | No delegada por defecto (JIT provisioning + rol fijo) | No delegada en este POC (mismo patrón aplicado) |

La delegación de identidad no es lo mismo que delegación de autorización - ambos protocolos resuelven "¿quién eres?" (o, en el caso puro de OAuth, "¿qué puedes tocar en otro sistema?"), pero ninguno resuelve por sí solo "¿qué puedes hacer aquí?" salvo que se diseñe explícitamente para ello, con un proveedor de alto trust dispuesto a compartir esa información.