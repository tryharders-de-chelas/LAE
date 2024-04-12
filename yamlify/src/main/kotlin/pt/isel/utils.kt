package pt.isel

import kotlin.reflect.KClass

fun convertType(value: String, targetType: KClass<*>): Any? {
    if(targetType.isInstance(value))
        return value

    return when (targetType) {
        Boolean::class -> value.toBooleanStrictOrNull()
        Char::class -> value.firstOrNull()
        Short::class -> value.toShortOrNull()
        Int::class -> value.toIntOrNull()
        Long::class -> value.toLongOrNull()
        Float::class -> value.toFloatOrNull()
        Double::class -> value.toDoubleOrNull()
        String::class -> value
        else -> null //invalid type
    } as Any
}