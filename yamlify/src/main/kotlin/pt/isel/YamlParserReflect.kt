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
            val type=param.type.jvmErasure
            argTypeMap[param.name!!] = type
            argKParameterName[param] = param.findAnnotation<YamlArg>()?.paramName ?: param.name!!
            param.findAnnotation<YamlConvert>()?.newClass?.also {
                yamlConvertMap[param] = it
            }
            val valueType = if(
                type.javaPrimitiveType == null &&
                type != String::class &&
                type != List::class &&
                type != Sequence::class &&
                !yamlConvertMap.containsKey(param)
                ) Map::class.starProjectedType.jvmErasure else type
            conversionMap[type] = convert(param, valueType)
        }
    }

/*
    ctor.parameters.forEach {
        it.findAnnotation<YamlConvert>()?.let { yamlConvert ->
            map[it] = yamlConvert.newClass
        }
    }
    map.toMap()

 */

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
            val paramValue = args[name]!!
            ctorArgs[kParameter] = conversionMap[typeClass]!!(kParameter, paramValue)
        }
        return ctor.callBy(ctorArgs)
    }



    private fun convert(param: KParameter, value: Any): (KParameter, Any) -> Any{
        when {
            (param.type.jvmErasure.javaPrimitiveType != null || param.type.jvmErasure == String::class) ->
                when (param.type.jvmErasure) {
                    Boolean::class -> return { _, v: Any ->
                        (v as String).toBoolean()
                    }
                    Char::class -> return{ _, v:Any ->
                        (v as String).first()
                    }
                    Short::class -> return{ _, v:Any ->
                        (v as String).toShort()
                    }
                    Int::class ->  return{ _, v:Any ->
                        (v as String).toInt()
                    }
                    Long::class -> return{ _, v:Any ->
                        (v as String).toLong()
                    }
                    Float::class -> return { _, v: Any ->
                        (v as String).toFloat()
                    }
                    Double::class -> return { _, v: Any ->
                        (v as String).toDouble()
                    }

                    else -> return { _, v: Any ->
                        v as String
                    }
                }
            param.type.jvmErasure == List::class -> return { p: KParameter, v: Any ->
                    val parser = yamlParser(p.type.arguments[0].type!!.jvmErasure)
                    @Suppress("UNCHECKED_CAST")
                    (v as List<Map<String, Any>>).map { parser.newInstance(it) }
            }
            (param.type.jvmErasure == Sequence::class) -> return { p: KParameter, v: Any ->
                    val parser = yamlParser(p.type.arguments[0].type!!.jvmErasure)
                    @Suppress("UNCHECKED_CAST")
                    (v as Iterable<Map<String, Any>>).map { parser.newInstance(it) }.asSequence()
            }
            else ->
                if(value == Map::class) return { p: KParameter, v: Any ->
                @Suppress("UNCHECKED_CAST")
                yamlParser(p.type.jvmErasure).newInstance(v as Map<String, Any>)
                }//12
                else return ret@ { p: KParameter, v: Any ->
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
}
