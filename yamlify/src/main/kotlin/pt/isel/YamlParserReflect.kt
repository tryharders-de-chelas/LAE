package pt.isel

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.full.*
import kotlin.reflect.jvm.jvmErasure

/**
 * A YamlParser that uses reflection to parse objects.
 */
class YamlParserReflect<T : Any> private constructor(type: KClass<T>) : AbstractYamlParser<T>(type) {
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

        private val yamlParserConstructors = mutableMapOf<KClass<*>, KFunction<*>>()

        fun <T : Any> yamlParserConstructor(type: KClass<T>): KFunction<T> {
            return yamlParserConstructors.getOrPut(type) {
                type.constructors.first()
            } as KFunction<T>
        }

        private val yamlParserMemberProps = mutableMapOf<KClass<*>, Collection<KProperty<*>>>()

        fun <T : Any> yamlParserMemberProps(type: KClass<T>): Collection<KProperty<*>> {
            return yamlParserMemberProps.getOrPut(type) {
                type.memberProperties
            }
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

    private val constructor = yamlParserConstructor(type)
    private val memberProps = yamlParserMemberProps(type)
    private val yamlArgAnnotations: Map<String, YamlArg?> =
        memberProps.associate { it.name to it.findAnnotation<YamlArg>() }
    private val yamlConvertAnnotations: Map<String, YamlConvert?> =
        memberProps.associate { it.name to it.findAnnotation<YamlConvert>() }
    private val ctorParamsMap = mutableMapOf<KParameter, Any?>()

    override fun newInstance(args: Map<String, Any>): T {
        for (param in constructor.parameters){
            val paramName = param.name ?:continue
            val y = yamlArgAnnotations[paramName]?.paramName
            if(!args.containsKey(paramName) && !args.containsKey(y))
                continue
           // val paramAnnotationValue = yamlArgAnnotations[paramName]?.paramName
            val paramValue = args[paramName] ?: args[y]!!
            val converter = yamlConvertAnnotations[paramName]
            val paramType = param.type.jvmErasure

            ctorParamsMap[param] = when{
                converter!=null -> {
                    val newClassRef = converter.newClass
                    newClassRef.declaredFunctions.first().call(
                        newClassRef.primaryConstructor!!.callBy(emptyMap()),
                        paramValue
                    )
                }
                paramType == Sequence::class -> {
                    createParserAndInstanceForCollection(param.type.arguments.first().type!!.jvmErasure, paramValue).asSequence()
                }
                paramType == List::class -> {
                    createParserAndInstanceForCollection(param.type.arguments.first().type!!.jvmErasure, paramValue)
                }
                paramType == String::class || paramType.javaPrimitiveType != null -> {
                    convertType((paramValue as String), paramType)
                }
                else -> {
                    createParserAndInstance(paramType, paramValue)
                }
            }
        }

        return constructor.callBy(ctorParamsMap)
    }


    private fun createParserAndInstance(paramType: KClass<*>, args: Any) =
        yamlParser(paramType).newInstance(args as Map<String, Any>)

    private fun createParserAndInstanceForCollection(paramType: KClass<*>, args: Any) =
        (args as Iterable<Map<String, Any>>).map {
            yamlParser(paramType).newInstance(it)
        }


}
