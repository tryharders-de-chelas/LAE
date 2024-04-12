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


    private fun primitiveList(iter: Iterator<String>,firstLine:String): List<Any> {
        val mutableList:MutableList<Any> = mutableListOf()
        val baseIndent: Int = countLeadingSpaces(firstLine)
        var key= firstLine
        while(iter.hasNext()){
            mutableList.add(key)
            key = iter.next().split("-")[1]
            if(baseIndent != countLeadingSpaces(key)) break
        }
        return mutableList
    }
    private fun objectList(iter: ListIterator<String>, baseIndent: Int): List<Any> {
        val mutableList:MutableList<Any> = mutableListOf()
        var (key, value) = iter.next().split(":")
        //val baseIndent: Int = countLeadingSpaces(key)
        val currentMap = mutableMapOf<String,Any>()
        while(iter.hasNext()){
            val trimmedKey = key.trim()
            when {
                trimmedKey == "-" -> {
                    if(currentMap.isNotEmpty()){
                    mutableList.add(currentMap)
                    currentMap.clear()
                    }
                }
                key.isNotBlank() && value.isNotBlank() -> currentMap[key] = value.trim()
                else -> mutableList.add(parseToMap(iter))
            }

            if(!iter.hasNext()) {
                if (currentMap.isNotEmpty())
                    mutableList.add(currentMap)
                break
            }

            iter.next().split(":").also {
                if(it.size == 1){
                    key = it[0]
                    value = ""
                } else {
                    key = it[0]
                    value = it[1]
                }
            }
            if(baseIndent < countLeadingSpaces(key)){
                if (currentMap.isNotEmpty())
                    mutableList.add(currentMap)
                break
            }
        }
        return mutableList.toList()
    }


    private fun isList(iter:ListIterator<String>,value:String, baseIndent: Int):List<Any> =
        if(value.isBlank())
            objectList(iter, baseIndent)
        else
            primitiveList(iter,value)

    private fun objectOrList(iter:ListIterator<String>,baseIndent: Int){
        val value = iter.next()
        if(value.contains(":"))parseToMap(iter)
        else isList(iter,value.split("-")[1],baseIndent)

    }


    private fun parseToMap(iter: ListIterator<String>, nestedMapKey: String? = null): Map<String, Any> {
        val paramsMap = mutableMapOf<String, Any>()
        var key : String
        var value : String
            iter.next().split(":").also {
                if(it.size == 1){
                    key = it[0]
                    value = ""
                } else {
                key = it[0]
                value = it[1]
                }
            }
        val baseIndent: Int = countLeadingSpaces(key)
        while (true){
            val trimmedKey = key.trim()
            when {
                trimmedKey == "-" -> {
                    paramsMap[nestedMapKey ?: trimmedKey] = isList(iter, value.trim(), countLeadingSpaces(key))
                }
                value.isBlank() -> paramsMap[trimmedKey] =parseToMap(iter, trimmedKey) //objectOrList(iter,baseIndent)//
                else -> paramsMap[trimmedKey] = value.trim()
            }

            if (iter.hasNext())
                iter.next().split(":").also {
                    key = it[0]
                    value = it[1]
                }
            else
                break
            if(baseIndent != countLeadingSpaces(key)){
                iter.previous()
                break
            }
        }
        return paramsMap
    }

    private fun List<String>.parseToMap2(): Map<String, Any> {
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
                        nestedLists.add(nestedLines.parseToMap2())
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
                paramsMap[currentKey] = nestedLines.parseToMap2()
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
            paramsMap[currentKey] = nestedLines.parseToMap2()
        }

        if (nestedLists.isNotEmpty()) {
            if (nestedLines.isNotEmpty())
                nestedLists.add(nestedLines.parseToMap2())
            paramsMap[currentKey] = nestedLists
        }

        return paramsMap
    }

    private fun List<String>.parseValues(): Map<String, Any> = associateWith { it }

    final override fun parseObject(yaml: Reader): T {
        val argsIter = yaml.readText().trimIndent().lines().listIterator()
        val parsedArgs = parseToMap(argsIter)
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
                        val obj = newInstance(nestedLines.parseToMap2())
                        resultList.add(obj)
                        nestedLines.clear()
                    }
                    continue
                }

                nestedLines.add(line)
            }

            if(nestedLines.isNotEmpty()){
                val obj = newInstance(nestedLines.parseToMap2())
                resultList.add(obj)
                nestedLines.clear()
            }

        }
        return resultList
    }
}
