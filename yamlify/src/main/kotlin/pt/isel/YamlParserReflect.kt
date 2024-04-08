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

        val key = yamlParsers.entries.find { (_, v) -> v == this }?.key ?: throw IllegalArgumentException()

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

        for (param in constructor.parameters.filter { !it.isOptional || (it.isOptional && args.containsKey(it.name)) }){
            when{
                param.type.jvmErasure == String::class || param.type.jvmErasure.javaPrimitiveType != null -> {
                    ctorParams[param] = convertType((args[param.name] as String).trim(), param.type.jvmErasure)
                }
                param.type.jvmErasure == Sequence::class -> {
                    ctorParams[param] =
                        createParserAndInstanceForCollection(
                            param.type.arguments.first().type!!.jvmErasure,
                            args[param.name]!!
                        ).asSequence()

                }
                param.type.jvmErasure == List::class -> {
                    ctorParams[param] =
                        createParserAndInstanceForCollection(
                            param.type.arguments.first().type!!.jvmErasure,
                            args[param.name]!!
                        )
                }
                else -> {
                    ctorParams[param] = createParserAndInstance(param.type.jvmErasure, args[param.name]!!)
                }
            }
        }

        return constructor.callBy(ctorParams) as T
    }


    private fun createParserAndInstance(paramType: KClass<*>, args: Any) =
        YamlParserReflect.yamlParser(paramType).newInstance(args as Map<String, Any>)

    private fun createParserAndInstanceForCollection(paramType: KClass<*>, args: Any) =
        (args as Iterable<Map<String, Any>>).map {
            YamlParserReflect.yamlParser(paramType).newInstance(it)
        }
}
