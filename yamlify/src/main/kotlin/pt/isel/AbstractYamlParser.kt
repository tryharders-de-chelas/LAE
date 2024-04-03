package pt.isel

import java.io.Reader
import kotlin.reflect.KClass

abstract class AbstractYamlParser<T : Any>(private val type: KClass<T>) : YamlParser<T> {
    /**
     * Used to get a parser for other Type using this same parsing approach.
     */
    abstract fun <T : Any> yamlParser(type: KClass<T>) : AbstractYamlParser<T>
    /**
     * Creates a new instance of T through the first constructor
     * that has all the mandatory parameters in the map and optional parameters for the rest.
     */
    abstract fun newInstance(args: Map<String, Any>): T

    private fun isPrimitiveType(cls: KClass<*>): Boolean {
        return cls.javaPrimitiveType != null
    }

    private fun isStringType(cls: KClass<*>): Boolean {
        return cls == String::class
    }

    private fun List<String>.parseToHashmap(): Map<String, Any> {
        val parametersMap = mutableMapOf<String, Any>()
        // TODO: Make this work with deeper nesting
        var currentKey = ""

        for(line in this){
            if (line.isBlank())
                continue

            val (argument, parameter) = line.split(":")

            if(!argument.startsWith("  "))
                currentKey = ""

            if(parameter.isEmpty()) {
                val nestedMap = mutableMapOf<String, Any>()
                parametersMap[argument] = nestedMap
                currentKey = argument
            } else {
                if(currentKey in parametersMap && parametersMap[currentKey] is MutableMap<*, *>){
                    (parametersMap[currentKey] as MutableMap<String, Any>)[argument.trim()] = parameter.trim()
                } else {
                    parametersMap[argument] = parameter
                }
            }
        }
        return parametersMap.toMap()
    }

    private fun List<String>.parseValues(): Map<String, Any> {
        val parameterMap = mutableMapOf<String, Any>()
        for(line in this){
            parameterMap[line] = line
        }
        return parameterMap.toMap()
    }

    final override fun parseObject(yaml: Reader): T {

        val args = yaml.readText().trimIndent().lines().parseToHashmap()
        return newInstance(args)
    }


    final override fun parseList(yaml: Reader): List<T> {
        val l = mutableListOf<T>()
        val objects = yaml.readText().split("-").filter { it.isNotBlank() }
        if(isStringType(type) || isPrimitiveType(type)){
            for (obj in objects){
                l.add(convertType(obj.trim(), type) as T)
            }
        } else {
            for (obj in objects){
                val args = obj.trimIndent().lines().parseToHashmap()
                l += newInstance(args)
            }
        }
        return l
    }
}
