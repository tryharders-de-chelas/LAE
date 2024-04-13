package pt.isel

import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class YamlConvert(val newClass:KClass<*>)
