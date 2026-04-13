# ether-di

Bloques de construcción para inyección de dependencias explícita en Java 21+.  
Sin reflexión, sin anotaciones, sin magia. Compatible con GraalVM native-image.

## Filosofía

`ether-di` no es un framework de DI — es un conjunto mínimo de utilidades que hacen que el **constructor injection** explícito sea seguro, limpio y predecible:

| Clase | Responsabilidad |
|---|---|
| `Lazy<T>` | Inicialización perezosa thread-safe (doble verificación de bloqueo) |
| `Closer` | Cierre de recursos en orden LIFO con acumulación de excepciones suprimidas |
| `Bootstrap<C>` | Arranque genérico de aplicación: warmup, shutdown hook y `AutoCloseable` |

No hay contenedor IoC. El grafo de dependencias se construye en Java puro, en código que puedes leer, depurar y refactorizar sin magia.

---

## Instalación

### Maven

```xml
<dependency>
    <groupId>dev.rafex.ether.di</groupId>
    <artifactId>ether-di</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```kotlin
implementation("dev.rafex.ether.di:ether-di:1.0.0")
```

**Requiere Java 21 o superior.**

---

## Componentes

### `Lazy<T>` — inicialización perezosa thread-safe

Evalúa el supplier exactamente una vez, en el primer `get()`. Después de la inicialización el supplier es elegible para GC.

```java
import dev.rafex.ether.di.Lazy;

// El supplier NO se ejecuta aquí
var config   = new Lazy<>(AppConfig::load);
var database = new Lazy<>(() -> DataSource.create(config.get()));
var cache    = new Lazy<>(() -> new RedisCache(config.get().redisUrl()));

// Se inicializa en el primer acceso (thread-safe)
AppConfig cfg = config.get();

// Llamadas posteriores devuelven el mismo objeto sin re-ejecutar el supplier
AppConfig same = config.get(); // mismo objeto

// Saber si ya fue inicializado
boolean ready = config.isInitialized(); // true
```

**Caso de uso típico — contenedor de dependencias:**

```java
public class AppContainer {

    private final Lazy<AppConfig>  config     = new Lazy<>(AppConfig::load);
    private final Lazy<DataSource> dataSource = new Lazy<>(() ->
            DataSourceFactory.create(config.get()));
    private final Lazy<UserRepository> users  = new Lazy<>(() ->
            new JdbcUserRepository(dataSource.get()));
    private final Lazy<UserService>    service = new Lazy<>(() ->
            new UserServiceImpl(users.get()));

    public UserService userService() { return service.get(); }
    public DataSource  dataSource()  { return dataSource.get(); }
}
```

> **Thread-safety:** 16 hilos llamando `get()` simultáneamente producen exactamente una inicialización. Ver `TestLazy.concurrentGetInitializesOnce`.

---

### `Closer` — gestión de recursos en orden LIFO

`Closer` implementa `AutoCloseable`. Registra recursos y los cierra en orden inverso (último en registrarse, primero en cerrarse). Si varios recursos lanzan excepción, propaga la primera y adjunta las demás como `suppressed`.

```java
import dev.rafex.ether.di.Closer;

var closer = new Closer();

// Registrar recursos — se cierran en orden inverso al registrarse
var pool  = closer.register(DataSourceFactory.create(config));
var cache = closer.register(new CacheManager());
var http  = closer.register(new HttpServer(config.port()));

// Uso con try-with-resources (recomendado en tests y CLI)
try (closer) {
    runApplication(pool, cache, http);
}
// http cerrado → cache cerrado → pool cerrado
```

**Encadenamiento en una sola expresión:**

```java
var closer = new Closer();
var server = closer.register(HttpServer.start(8080)); // register devuelve el mismo objeto
```

**Cierre idempotente:**

```java
closer.close();
closer.close(); // no-op, no lanza excepción
closer.isClosed(); // true
```

**Manejo de excepciones múltiples:**

```java
var closer = new Closer();
closer.register(() -> { throw new IllegalStateException("error en recurso A"); });
closer.register(() -> { throw new IllegalStateException("error en recurso B"); });

try {
    closer.close();
} catch (RuntimeException e) {
    // e.getMessage() == "error en recurso B"  (LIFO — B es el último registrado, primero en cerrarse)
    // e.getSuppressed()[0].getMessage() == "error en recurso A"
}
```

---

### `Bootstrap` — arranque de aplicación con ciclo de vida completo

`Bootstrap` une las tres etapas del ciclo de vida: **creación → warmup → shutdown**.

```java
import dev.rafex.ether.di.Bootstrap;

var runtime = Bootstrap.start(
    AppContainer::new,       // 1. factory: crea el contenedor
    AppContainer::warmup,    // 2. warmup: inicializa componentes de forma eager (fail-fast)
    c -> c.shutdown()        // 3. onShutdown: se llama antes de cerrar recursos (JVM shutdown hook)
);
```

El `runtime` devuelto es `AutoCloseable` e implementa `Bootstrap.Runtime<C>`:

```java
Bootstrap.Runtime<AppContainer> runtime = Bootstrap.start(AppContainer::new);

AppContainer container = runtime.container(); // contenedor de dependencias
Closer       closer    = runtime.closer();    // cierre de recursos

// Cerrar manualmente
runtime.close();
```

**Ejemplo completo — aplicación HTTP:**

```java
public class Main {

    public static void main(String[] args) {
        var runtime = Bootstrap.start(
            AppContainer::new,
            AppContainer::warmup,
            c -> LOG.info("Shutting down...")
        );

        // El JVM shutdown hook ya está registrado — Ctrl+C lo activa
        JettyServer.start(runtime.container());
    }
}
```

**Ejemplo con try-with-resources (tests, CLI):**

```java
@Test
void integrationTest() throws Exception {
    try (var runtime = Bootstrap.start(TestContainer::new)) {
        var service = runtime.container().userService();
        assertNotNull(service.findById(1L));
    }
    // recursos cerrados automáticamente al salir del bloque
}
```

**Sobrecargas disponibles:**

```java
// Solo factory (sin warmup ni onShutdown)
Bootstrap.start(AppContainer::new);

// Factory + warmup (sin onShutdown)
Bootstrap.start(AppContainer::new, AppContainer::warmup);

// Factory + warmup + onShutdown
Bootstrap.start(AppContainer::new, AppContainer::warmup, c -> c.logStats());
```

> Si el contenedor implementa `AutoCloseable`, `Bootstrap` lo registra automáticamente en el `Closer` — no necesitas registrarlo manualmente.

---

## Patrón completo: contenedor explícito + Bootstrap

```java
// AppContainer.java
public class AppContainer implements AutoCloseable {

    private final Closer closer = new Closer();

    private final Lazy<AppConfig>      config  = new Lazy<>(AppConfig::load);
    private final Lazy<DataSource>     db      = new Lazy<>(() ->
            closer.register(DataSourceFactory.create(config.get())));
    private final Lazy<UserRepository> users   = new Lazy<>(() ->
            new JdbcUserRepository(db.get()));
    private final Lazy<UserService>    service = new Lazy<>(() ->
            new UserServiceImpl(users.get()));

    /** Warmup: inicializa todo eagerly para detectar errores en el arranque. */
    public void warmup() {
        service.get(); // dispara la cadena completa de inicialización
    }

    public UserService userService() { return service.get(); }

    @Override
    public void close() {
        closer.close(); // cierra DataSource en LIFO
    }
}

// Main.java
public class Main {
    public static void main(String[] args) {
        Bootstrap.start(
            AppContainer::new,
            AppContainer::warmup
        );
        // JVM shutdown hook cierra AppContainer → closer → DataSource
    }
}
```

---

## Comparación con alternativas

| | ether-di | Spring DI | CDI | Guice |
|---|---|---|---|---|
| Reflexión | No | Sí | Sí | Sí |
| Anotaciones | No | Sí | Sí | Sí |
| GraalVM native | Sí, sin config | Requiere hints | Requiere hints | Requiere hints |
| Grafo visible en código | Sí | No | No | Parcial |
| Tamaño del jar | ~10 KB | ~1 MB+ | ~500 KB | ~700 KB |

---

## Requisitos

- Java 21+
- Sin dependencias en tiempo de ejecución

---

## Licencia

MIT — ver [LICENSE](LICENSE).
