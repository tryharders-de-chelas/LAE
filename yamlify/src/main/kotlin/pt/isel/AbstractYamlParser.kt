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

    private fun isPrimitiveType(cls: KClass<*> = type): Boolean {
        return cls.javaPrimitiveType != null
    }

    private fun isStringType(cls: KClass<*> = type): Boolean {
        return cls == String::class
    }

    private fun countLeadingSpaces(input: String): Int =
        input.takeWhile { it.isWhitespace() }.length

    private fun isListStart(line: String, baseIndent: Int): Boolean =
        line.startsWith(
            " ".repeat(baseIndent) + " ".repeat(2) + "-"
        )


    private fun List<String>.parseToMap(): Map<String, Any> {
        val paramsMap = mutableMapOf<String, Any>()
        var currentKey = ""
        var nestedLines = mutableListOf<String>()
        var nestedLists = mutableListOf<Map<String, Any>>()
        var listMode = false
        val baseIndent = countLeadingSpaces(this.first())
        var currentIndent: Int

        for (line in this) {
            currentIndent = countLeadingSpaces(line)
            if (line.isBlank())
                continue

            if (listMode) {
                if (currentIndent > baseIndent) {
                    if (isListStart(line, baseIndent)) {
                        nestedLists.add(nestedLines.parseToMap())
                        nestedLines = mutableListOf()
                    } else {
                        nestedLines.add(line)
                    }
                    continue
                } else {
                    paramsMap[currentKey] = nestedLists
                    currentKey = ""
                    nestedLines = mutableListOf()
                    listMode = false
                }
            }

            if (isListStart(line, baseIndent)) {
                listMode = true
                continue
            }



            if (currentIndent > baseIndent && currentKey.isNotBlank()) {
                nestedLines.add(line)
                continue
            }

            if (nestedLines.isNotEmpty()) {
                paramsMap[currentKey] = nestedLines.parseToMap()
                currentKey = ""
                nestedLines = mutableListOf()
                listMode = false
            }

            val (key, value) = line.split(":").map { it.trim() }
            if (value.isBlank()) {
                currentKey = key
            }

            paramsMap[key] = value
        }

        if (nestedLines.isNotEmpty() && !listMode) {
            paramsMap[currentKey] = nestedLines.parseToMap()
        }

        if (nestedLists.isNotEmpty()) {
            if (nestedLines.isNotEmpty())
                nestedLists.add(nestedLines.parseToMap())
            paramsMap[currentKey] = nestedLists
        }

        return paramsMap
    }

    private fun List<String>.parseValues(): Map<String, Any> = associateWith { it }

    final override fun parseObject(yaml: Reader): T {
        val args = yaml.readText().trimIndent().lines()
        val parsedArgs = args.parseToMap()
        return newInstance(parsedArgs)
    }


    final override fun parseList(yaml: Reader): List<T> {
        val resultList = mutableListOf<T>()
        if(isStringType() || isPrimitiveType()){
            for (obj in yaml.readText().split("-")){
                if(obj.isBlank())
                    continue
                resultList.add(convertType(obj.trim(), type) as T)
            }
        } else {
            val nestedLines = mutableListOf<String>()
            val objects = yaml.readText().trimIndent().lines()
            for(line in objects){
                if(line.startsWith("-")){
                    if(nestedLines.isNotEmpty()){
                        val obj = newInstance(nestedLines.parseToMap())
                        resultList.add(obj)
                        nestedLines.clear()
                    }
                    continue
                }

                nestedLines.add(line)
            }

            if(nestedLines.isNotEmpty()){
                val obj = newInstance(nestedLines.parseToMap())
                resultList.add(obj)
                nestedLines.clear()
            }

        }
        return resultList
    }
}
