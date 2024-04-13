package pt.isel

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

/**
 * A YamlParser that uses reflection to parse objects.
 */
class YamlParserReflect<T : Any> private constructor(type: KClass<T>) : AbstractYamlParser<T>(type) {

    init {

    }

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
        require(
            argKParameterName.none { (key, value) -> !key.isOptional && !args.containsKey(value) }
        ) { "Map must not be empty" }
        val ctorArgs = mutableMapOf<KParameter, Any>()
        for ((kParameter, name) in argKParameterName){
            if(kParameter.isOptional && !args.containsKey(name))
                continue
            val typeClass = argTypeMap[kParameter.name!!]!!
            val paramValue = args[name]
            when{
                (yamlConvertMap.containsKey(kParameter)) -> {
                    ctorArgs[kParameter] = yamlConvertMap[kParameter]!!
                        .declaredFunctions
                        .first()
                        .call(
                            yamlConvertMap[kParameter]!!.primaryConstructor!!.callBy(emptyMap()),
                            paramValue
                        ) as Any
                }
                paramValue is Map<*, *> -> {
                    yamlParser(typeClass).newInstance(paramValue as Map<String, Any>).let { obj ->
                        ctorArgs[kParameter] = obj
                    }
                }
                paramValue is List<*> -> {
                    if(kParameter.type.jvmErasure == List::class){
                        val parser = yamlParser(kParameter.type.arguments[0].type!!.jvmErasure)
                        ctorArgs[kParameter] = (paramValue).map { parser.newInstance(it as Map<String, Any>) }
                    } else { // is Sequence<*>
                        val parser = yamlParser(kParameter.type.arguments[0].type!!.jvmErasure)
                        ctorArgs[kParameter] =
                            (paramValue as Iterable<Map<String, Any>>)
                                .map { parser.newInstance(it) }
                                .asSequence()
                    }
                }
                else -> {
                    convert(paramValue as String, typeClass)?.let {
                        ctorArgs[kParameter] = it
                    }
                }
            }
        }
        return ctor.callBy(ctorArgs)
    }

    private val ctor = type.primaryConstructor!!

    private val argTypeMap = run {
        val map = mutableMapOf<String, KClass<*>>()
        ctor.parameters.forEach {
            map[it.name!!] = it.type.jvmErasure
        }
        map.toMap()
    }

    private val argKParameterName = run {
        val map = mutableMapOf<KParameter, String>()
        ctor.parameters.forEach {
            map[it] = it.findAnnotation<YamlArg>()?.paramName ?: it.name!!
        }
        map.toMap()
    }

    private val yamlConvertMap = run {
        val map = mutableMapOf<KParameter, KClass<*>>()
        ctor.parameters.forEach {
            it.findAnnotation<YamlConvert>()?.let { yamlConvert ->
                map[it] = yamlConvert.newClass
            }
        }
        map.toMap()
    }

    //private val conversionMap = mutableMapOf<KClass<*>, (KParameter, V)>()


    private fun convert(value: String, type: KClass<*>): Any? =
         when(type) {
            String::class -> value
            Boolean::class -> value.toBooleanStrictOrNull()
            Char::class -> value.firstOrNull()
            Short::class -> value.toShortOrNull()
            Int::class -> value.toIntOrNull()
            Long::class -> value.toLongOrNull()
            Float::class -> value.toFloatOrNull()
            Double::class -> value.toDoubleOrNull()
            else -> null
        }




}
