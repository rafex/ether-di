# ether-di

Bloques de construcción para inyección de dependencias explícita en Java 21+, sin reflexión, sin anotaciones, sin magia.

## Filosofía

`ether-di` no es un framework de DI — es un conjunto de utilidades que hacen que el **constructor injection** explícito sea seguro, limpio y reutilizable:

- `Lazy<T>` — inicialización perezosa thread-safe con doble verificación de bloqueo
- `Closer` — gestión de recursos LIFO con acumulación de excepciones suprimidas
- `Bootstrap<C>` — arranque genérico con warmup, shutdown hook y `AutoCloseable`

Zero reflexión. Zero annotations. Compatible con GraalVM native-image.

## Instalación

```xml
<dependency>
    <groupId>dev.rafex.ether.di</groupId>
    <artifactId>ether-di</artifactId>
</dependency>
```

## Licencia

MIT — ver [LICENSE](LICENSE).
