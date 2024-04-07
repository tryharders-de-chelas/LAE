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

    private fun countLeadingSpaces(input: String): Int {
        var count = 0
        for (char in input) {
            if (char.isWhitespace()) {
                count++
            } else {
                break
            }
        }
        return count
    }

    private fun List<String>.parseToHashmap(): Map<String, Any> {
        val parametersMap = mutableMapOf<String, Any>()
        // TODO: Make this work with deeper nesting
        val baseDepth = countLeadingSpaces(this.first())
        var nested = ""
        val nestedLines = mutableListOf<String>()
        val l = mutableListOf<Map<String, Any>>()
        var listMode = false
        for(line in this){
            if (line.isBlank())
                continue

            if(listMode){
                if(baseDepth < countLeadingSpaces(line)){
                    if(line.startsWith(" ".repeat(baseDepth) + " ".repeat(2) + "-")){
                        l.add(nestedLines.parseToHashmap())
                        nestedLines.clear()
                        //listMode = false
                    } else {
                        nestedLines.add(line)
                    }
                    continue
                } else {
                    parametersMap[nested] = l
                    nested = ""
                    nestedLines.clear()
                    listMode = false
                }
            }

            if(line.startsWith(" ".repeat(baseDepth) + " ".repeat(2) + "-")){
                listMode = true
                continue
            }

            val (argument, parameter) = line.split(":")

            if(parameter.isBlank()){
                nested = argument.trim()
            }

            if(baseDepth < countLeadingSpaces(line) && nested.isNotBlank()){
                nestedLines.add(line)
                continue
            }

            if(nestedLines.isNotEmpty()){
                parametersMap[nested] = nestedLines.parseToHashmap()
                nested = ""
                nestedLines.clear()
                listMode = false
            }

            parametersMap[argument.trim()] = parameter.trim()
        }

        if(l.isNotEmpty()){
            if(nestedLines.isNotEmpty())
                l.add(nestedLines.parseToHashmap())
            parametersMap[nested] = l
        }

        val x = 1
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
        val args = yaml.readText().trimIndent().lines()
        val hm = args.parseToHashmap()
        return newInstance(hm)
    }


    final override fun parseList(yaml: Reader): List<T> {
        val l = mutableListOf<T>()
        if(isStringType(type) || isPrimitiveType(type)){
            val objects = yaml.readText().split("-").filter { it.isNotBlank() }
            for (obj in objects){
                l.add(convertType(obj.trim(), type) as T)
            }
        } else {
            val nestedLines = mutableListOf<String>()
            val objects = yaml.readText().trimIndent().lines()
            for(line in objects){
                if(line.startsWith("-")){
                    if(nestedLines.isNotEmpty()){
                        val obj = newInstance(nestedLines.parseToHashmap())
                        l.add(obj)
                        nestedLines.clear()
                    }
                    continue
                }

                nestedLines.add(line)
            }

            if(nestedLines.isNotEmpty()){
                val obj = newInstance(nestedLines.parseToHashmap())
                l.add(obj)
                nestedLines.clear()
            }

        }
        return l
    }
}
