package pt.isel

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

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

        val key=yamlParsers.entries.find { (k,v) -> v== this }?.key ?: throw IllegalArgumentException()
        val constructor = key
            .constructors
            .firstOrNull{ constructor ->
                constructor
                    .parameters
                    .filter{ !it.isOptional }//TODO if they give optional?
                    .all{param -> args.containsKey(param.name) }
            }?: throw IllegalArgumentException()

        return constructor.callBy(args as Map<KParameter, Any?>) as T
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
