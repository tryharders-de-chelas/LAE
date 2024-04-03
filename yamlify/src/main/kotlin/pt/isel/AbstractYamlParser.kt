package pt.isel

import java.io.Reader
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

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
        return parametersMap
    }

    final override fun parseObject(yaml: Reader): T {

        val args = yaml.readText().trimIndent().lines().parseToHashmap()
        return newInstance(args)
    }


    final override fun parseList(yaml: Reader): List<T> {
        val args = yaml.readText().trimIndent().lines()
        val resultList = mutableListOf<T>()

        val listArguments = mutableListOf<String>()
        var inList = false

        for (line in args) {


            if (line.trimStart().startsWith("-")) {
                val newLine = line.removePrefix("-").trim()
                if (newLine.isBlank()) {
                    inList = true
                } else {
                    val map = parseSingleObject(newLine)
                    resultList.add(newInstance(map))
                    inList = false
                }
                if (listArguments.isNotEmpty()) {
                    val map = listArguments.parseToHashmap()
                    resultList.add(newInstance(map))
                    listArguments.clear()
                }
                listArguments.add(line)
            } else {
                if (inList) {
                    listArguments.add(line)
                } else {
                    val map = parseSingleObject(line)
                    resultList.add(newInstance(map))
                }
            }
        }

        if (inList && listArguments.isNotEmpty()) {
            val map = listArguments.parseToHashmap()
            resultList.add(newInstance(map))
        }

        return resultList
    }

    private fun parseSingleObject(line: String): T {
        return when(type){
            Int::class -> line.toInt() as T
            Long::class -> line.toLong() as T

            else -> line as T
        }
    }



}
