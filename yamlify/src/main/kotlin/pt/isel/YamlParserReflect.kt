package pt.isel

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
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

    private fun matchPropWithParam(srcProp: KProperty<*>, param: KParameter) : Boolean {
        if(srcProp.name == param.name) {
            return srcProp.returnType == param.type
        }
        val annot = srcProp.findAnnotation<YamlArg>() ?: return false
        return annot.paramName == param.name && srcProp.returnType == param.type
    }


    override fun newInstance(args: Map<String, Any>): T {

        val key = yamlParsers.entries.find { (_, v) -> v == this }?.key ?: throw IllegalArgumentException()

        val constructor = key
            .constructors
            .firstOrNull{ constructor ->
                constructor
                    .parameters
                    .filter{ !it.isOptional }
                    .all{
                        param ->
                        args.containsKey(param.name) ||
                        args.containsKey(
                            key.memberProperties.find{ matchPropWithParam(it, param) }?.findAnnotation<YamlArg>()?.paramName
                        )
                    }
            } ?: throw IllegalArgumentException()

        val ctorParamsMap = mutableMapOf<KParameter, Any?>()
        val ctorParams = constructor.parameters.filter { param ->
            args.containsKey(param.name) ||
            args.containsKey(
                key.memberProperties.find{ matchPropWithParam(it, param) }?.findAnnotation<YamlArg>()?.paramName
            )
        }

        for (param in ctorParams){
            val paramAnnotationValue = key.memberProperties.find{ it.name == param.name }?.findAnnotation<YamlArg>()?.paramName
            val paramValue = args[param.name] ?: args[paramAnnotationValue]!!
            val converter = key.memberProperties.find{ it.name == param.name }?.findAnnotation<YamlConvert>()?.newClass

            when{
                converter!=null -> {
                    val tentati= converter.members.find{it.name== "parse" && it.parameters.size==1 && it.parameters.first.type.classifier==String::class} as KFunction<*>

                    ctorParamsMap[param] =
                            tentati.call(paramValue)
                }

                param.type.jvmErasure == String::class || param.type.jvmErasure.javaPrimitiveType != null -> {
                    ctorParamsMap[param] =
                        convertType((paramValue as String), param.type.jvmErasure)
                }
                param.type.jvmErasure == Sequence::class -> {
                    ctorParamsMap[param] =
                        createParserAndInstanceForCollection(param.type.arguments.first().type!!.jvmErasure, paramValue).asSequence()
                }
                param.type.jvmErasure == List::class -> {
                    ctorParamsMap[param] =
                        createParserAndInstanceForCollection(param.type.arguments.first().type!!.jvmErasure, paramValue)
                }
                else -> {
                    ctorParamsMap[param] =
                        createParserAndInstance(param.type.jvmErasure, paramValue)
                }
            }
        }

        return constructor.callBy(ctorParamsMap) as T
    }


    private fun createParserAndInstance(paramType: KClass<*>, args: Any) =
        YamlParserReflect.yamlParser(paramType).newInstance(args as Map<String, Any>)

    private fun createParserAndInstanceForCollection(paramType: KClass<*>, args: Any) =
        (args as Iterable<Map<String, Any>>).map {
            YamlParserReflect.yamlParser(paramType).newInstance(it)
        }
}
