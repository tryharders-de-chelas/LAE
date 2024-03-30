package pt.isel

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.jvmErasure

/**
 * A YamlParser that uses reflection to parse objects.
 */
class YamlParserReflect<T : Any>(type: KClass<T>) : AbstractYamlParser<T>(type) {
    companion object {
        /**
         *Internal cache of YamlParserReflect instances.
         */
        private val yamlParsers: MutableMap<KClass<*>, YamlParserReflect<*>> = mutableMapOf()
        /**
         * Creates a YamlParser for the given type using reflection if it does not already exist.
         * Keep it in an internal cache of YamlParserReflect instances.
         */
        fun <T : Any> yamlParser(type: KClass<T>): AbstractYamlParser<T> {
            return yamlParsers.getOrPut(type) { YamlParserReflect(type) } as YamlParserReflect<T>
        }
    }
    /**
     * Used to get a parser for other Type using the same parsing approach.
     */
    override fun <T : Any> yamlParser(type: KClass<T>) = YamlParserReflect.yamlParser(type)
    /**
     * Creates a new instance of T through the first constructor
     * that has all the mandatory parameters in the map and optional parameters for the rest.
     */
    override fun newInstance(args: Map<String, Any>): T {
        // Get the primary constructor of type T

        val key=yamlParsers.entries.find { (_,v) -> v== this }?.key ?: throw IllegalArgumentException()
        val constructor = key
            .constructors
            .firstOrNull{ constructor ->
                constructor
                    .parameters
                    .filter{ !it.isOptional }//TODO if they give optional?
                    .all{param -> args.containsKey(param.name) }
            }?: throw IllegalArgumentException()

        val ctorParams = mutableMapOf<KParameter, Any?>()
        for (param in constructor.parameters.filter { !it.isOptional && it.name in args}){
            // TODO: Is that filter necessary??
            println("${param.name} is ${param.type}")
            ctorParams[param] = convertType(args[param.name]!!, param.type.jvmErasure)
        }
        println(ctorParams)
        return constructor.callBy(ctorParams) as T
    }

    private fun convertType(value: Any, targetType: KClass<*>): Any {
        println("Casting ${value} from ${value::class} to $targetType")
        if(targetType.isInstance(value))
            return value

        // TODO: Can we assume we will only be getting Ints?
        val ans = when (targetType) {
            String::class -> value.toString()
            Int::class -> {
                when (value) {
                    is String -> value.toIntOrNull()
                    is Int -> value
                    is Long -> value.toInt()
                    is Char -> value.code
                    else -> null
                }
            }
            Long::class -> {
                when (value) {
                    is String -> value.toLongOrNull()
                    is Int -> value.toLong()
                    is Long -> value
                    is Char -> value.code.toLong()
                    else -> null
                }
            }
            Char::class -> {
                when (value) {
                    is String -> value.firstOrNull()
                    is Int -> value.toChar()
                    is Long -> value.toInt().toChar()
                    is Char -> value
                    else -> null
                }
            }
            else -> null // Unsupported target type
        }
        return ans as Any
    }

    /***
     *  private val destCtor = destType
     *         .constructors
     *         .first { ctor ->
     *             ctor
     *                 .parameters
     *                 .filter { !it.isOptional }
     *                 .all { param -> srcType
     *                     .memberProperties
     *                     .any { it.name == param.name && it.returnType == param.type}
     *                 }
     *         }
     */
}
