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

        val key = yamlParsers.entries.find { (_,v) -> v== this }?.key ?: throw IllegalArgumentException()

        println("newInstance")
        val constructor = key
            .constructors
            .firstOrNull{ constructor ->
                constructor
                    .parameters
                    .filter{ !it.isOptional }//TODO if they give optional?
                    .all{param -> args.containsKey(param.name) }
            }?: throw IllegalArgumentException()

        val ctorParams = mutableMapOf<KParameter, Any?>()

        for (param in constructor.parameters.filter { !it.isOptional }){
            ctorParams[param] = convertType((args[param.name] as String).trim(), param.type.jvmErasure)
        }

        // TODO: Refactor this
        val ctorOtherParams = constructor.parameters.filter{ it.isOptional }.map { it.name }
        val optionalParams = args.keys.filter { it in ctorOtherParams }
        for (element in optionalParams){
            val nested = constructor.parameters.first { it.name == element }
            val smth = args[element] as Map<String, Any>
            ctorParams[nested] =
                YamlParserReflect.
                yamlParser(nested.type.classifier as KClass<*>).
                newInstance(smth)
        }

        return constructor.callBy(ctorParams) as T
    }

    private fun convertType(value: String, targetType: KClass<*>): Any {
        if(targetType.isInstance(value))
            return value

        // TODO: Can we assume we will only be getting Ints?
        println("Converting $value from ${value::class} to $targetType")
        return when (targetType) {
            Int::class -> value.toIntOrNull()
            Long::class -> value.toLongOrNull()
            Char::class -> value.firstOrNull()
            else -> null // Unsupported target type
        } as Any
    }
}
