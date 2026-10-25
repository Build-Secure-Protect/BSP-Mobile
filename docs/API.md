# BSP-Mobile API for other mods

Package `com.mrgregles.bsp_mobile.api`. Every call is a plain static method with the exact signature given, so a mod may find it by name with `MethodHandles.publicLookup().findStatic` when `bsp_mobile` is loaded instead of compiling against this jar. Nothing here changes a construct.

## `MobilePositions` (since 0.1.0)

A construct's blocks stand in the engine's shipyard: real blocks far away in the same dimension, drawn where the construct is. A mod that measures distances around such a block (BSP-Core around a Shatter Totem aboard, request M-R1) projects it into the world first. Each call is the identity, or false, for a position that is not aboard, so a caller may use them for every block.

| Call | What it answers |
|---|---|
| `public static Vec3 toWorld(Level level, Vec3 pos)` | the world position a point aboard a construct is drawn at; `pos` itself when it is not aboard |
| `public static BlockPos toWorld(Level level, BlockPos pos)` | the world block a block aboard is drawn at (its centre projected); `pos` itself when not aboard |
| `public static boolean isAboard(Level level, BlockPos pos)` | true when the position lies in a construct, not in the ordinary world |

```java
// BSP-Core, measuring from a totem that may be aboard a construct
BlockPos where = MobilePositions.toWorld(level, totemPos);
double d = Math.sqrt(where.distSqr(thief.blockPosition()));
```

Cheap to call: a lookup in the engine's ship table, no chunk loads.
