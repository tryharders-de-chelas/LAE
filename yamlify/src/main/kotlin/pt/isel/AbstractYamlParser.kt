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

    private fun List<String>.isListStart(line: String): Boolean =
        line.startsWith(
            " ".repeat(countLeadingSpaces(this.first())) + " ".repeat(2) + "-"
        )


    private fun List<String>.parseToMap(): Map<String, Any> {
        val paramsMap = mutableMapOf<String, Any>()
        var currentKey = ""
        val nestedLines = mutableListOf<String>()
        val nestedLists = mutableListOf<Map<String, Any>>()
        var listMode = false

        for (line in this) {
            if (line.isBlank())
                continue

            if (listMode) {
                if (countLeadingSpaces(line) > countLeadingSpaces(this.first())) {
                    if (isListStart(line)) {
                        nestedLists.add(nestedLines.parseToMap())
                        nestedLines.clear()
                    } else {
                        nestedLines.add(line)
                    }
                    continue
                } else {
                    paramsMap[currentKey] = nestedLists
                    currentKey = ""
                    nestedLines.clear()
                    listMode = false
                }
            }

            if (isListStart(line)) {
                listMode = true
                continue
            }

            val (key, value) = line.split(":")


            if (countLeadingSpaces(line) > countLeadingSpaces(this.first()) && currentKey.isNotBlank()) {
                nestedLines.add(line)
                continue
            }

            if (nestedLines.isNotEmpty()) {
                paramsMap[currentKey] = nestedLines.parseToMap()
                currentKey = ""
                nestedLines.clear()
                listMode = false
            }

            if (value.isBlank()) {
                currentKey = key.trim()
            }

            paramsMap[key.trim()] = value.trim()
        }

        if (nestedLines.isNotEmpty() && !listMode) {
            paramsMap[currentKey] = nestedLines.parseToMap()
        }

        if (nestedLists.isNotEmpty()) {
            if (nestedLines.isNotEmpty()) nestedLists.add(nestedLines.parseToMap())
            paramsMap[currentKey] = nestedLists
        }

        return paramsMap
    }

    private fun List<String>.parseValues(): Map<String, Any> = associateWith { it }

    final override fun parseObject(yaml: Reader): T {
        val args = yaml.readText().trimIndent().lines()
        val x= args.parseToMap()
        return newInstance(args.parseToMap())
    }


    final override fun parseList(yaml: Reader): List<T> {
        val resultList = mutableListOf<T>()
        if(isStringType() || isPrimitiveType()){
            val objects = yaml.readText().split("-").filter { it.isNotBlank() }
            for (obj in objects){
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
