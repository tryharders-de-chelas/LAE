package pt.isel

import kotlin.reflect.KClass

fun convertType(value: String, targetType: KClass<*>): Any {
    if(targetType.isInstance(value))
        return value

    // TODO: Can we assume we will only be getting Ints?
    println("Converting $value from ${value::class} to $targetType")
    return when (targetType) {
        Int::class -> value.toIntOrNull()
        Long::class -> value.toLongOrNull()
        Char::class -> value.firstOrNull()
        String::class -> value

        else -> null // Unsupported target type
    } as Any
}