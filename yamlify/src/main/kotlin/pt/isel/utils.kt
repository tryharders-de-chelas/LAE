package pt.isel

import kotlin.reflect.KClass

val primitiveMap: Map<KClass<*>, (Any) -> Any> =
    mapOf(
        Boolean::class to { v: Any -> (v as String).toBoolean() },
        Char::class to { v: Any -> (v as String).first() },
        Short::class to { v: Any -> (v as String).toShort() },
        Int::class to { v: Any -> (v as String).toInt() },
        Long::class to { v: Any -> (v as String).toLong() },
        Float::class to { v: Any -> (v as String).toFloat() },
        Double::class to { v: Any -> (v as String).toDouble() },
        String::class to { v: Any -> (v as String).trim() }
    )