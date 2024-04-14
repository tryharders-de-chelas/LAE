package pt.isel

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.*
import kotlin.reflect.jvm.jvmErasure

/**
 * A YamlParser that uses reflection to parse objects.
 */
class YamlParserReflect<T : Any> private constructor(type: KClass<T>) : AbstractYamlParser<T>(type) {

    private val ctor = type.primaryConstructor!!

    private val argTypeMap = mutableMapOf<String, KClass<*>>()

    private val argKParameterName = mutableMapOf<KParameter, String>()

    private val yamlConvertMap =mutableMapOf<KParameter, KClass<*>?>()

    private val conversionMap = mutableMapOf<KClass<*>, (KParameter, Any) -> Any>()

    init {
        for (param in ctor.parameters) {
            val paramType = param.type.jvmErasure
            argTypeMap[param.name!!] = paramType
            argKParameterName[param] = param.findAnnotation<YamlArg>()?.paramName ?: param.name!!
            param.findAnnotation<YamlConvert>()?.newClass?.also {
                yamlConvertMap[param] = it
            }
            conversionMap[paramType] = primitiveMap[paramType] ?: when (paramType) {
                List::class -> convertList(param)
                Sequence::class -> convertSequence(param)
                else -> {
                    val valueType =
                        if(yamlConvertMap[param] == null)
                            Map::class.starProjectedType.jvmErasure
                        else
                            paramType
                    convertRefTypes(valueType)
                }
            }
        }
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
            @Suppress("UNCHECKED_CAST")
            return yamlParsers.getOrPut(type) { YamlParserReflect(type) } as YamlParserReflect<T>
        }

        val primitiveMap: Map<KClass<*>, (KParameter, Any) -> Any> =
            mapOf(
                Boolean::class to { _, v: Any -> (v as String).toBoolean() },
                Char::class to { _, v: Any -> (v as String).first() },
                Short::class to { _, v: Any -> (v as String).toShort() },
                Int::class to { _, v: Any -> (v as String).toInt() },
                Long::class to { _, v: Any -> (v as String).toLong() },
                Float::class to { _, v: Any -> (v as String).toFloat() },
                Double::class to { _, v: Any -> (v as String).toDouble() },
                String::class to { _, v: Any -> v as String }
            )

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
            ctorArgs[kParameter] =
                primitiveMap[typeClass]?.invoke(kParameter, args[name]!!) ?:
                conversionMap[typeClass]!!(kParameter, args[name]!!)
        }
        return ctor.callBy(ctorArgs)
    }

    private fun convertList(param: KParameter): (KParameter, Any) -> Any {
        val parser = yamlParser(param.type.arguments[0].type!!.jvmErasure)
        return { _, v: Any ->
            @Suppress("UNCHECKED_CAST")
            (v as List<Map<String, Any>>).map { parser.newInstance(it) }
        }
    }

    private fun convertSequence(param: KParameter): (KParameter, Any) -> Any {
        val parser = yamlParser(param.type.arguments[0].type!!.jvmErasure)
        return { _, v: Any ->
            @Suppress("UNCHECKED_CAST")
            (v as Iterable<Map<String, Any>>).map { parser.newInstance(it) }.asSequence()
        }
    }

    private fun convertRefTypes(value: Any): (KParameter, Any) -> Any {
        // Nested Objects
        if(value == Map::class) return { p: KParameter, v: Any ->
            @Suppress("UNCHECKED_CAST")
            yamlParser(p.type.jvmErasure).newInstance(v as Map<String, Any>)
        } else return ret@ { p: KParameter, v: Any ->
            if(yamlConvertMap.containsKey(p)){
                return@ret yamlConvertMap[p]!!
                .declaredFunctions
                .first()
                .call(
                    yamlConvertMap[p]!!.primaryConstructor!!.callBy(emptyMap()),
                    v
                ) as Any
            } else {
                return@ret v
            }
        }
    }
}
