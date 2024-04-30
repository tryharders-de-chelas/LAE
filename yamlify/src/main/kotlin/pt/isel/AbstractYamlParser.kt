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

    private fun countLeadingSpaces(input: String): Int = input.takeWhile { it.isWhitespace() }.length

    private fun getNextLine(iter: ListIterator<String>, delimiter: String): Pair<String, String> {
        var key: String
        var value: String
        iter.next().split(delimiter, limit = 2).also {
            if(it.size == 1){
                key = it[0]
                value = ""
            } else {
                key = it[0]
                value = it[1]
            }
        }
        return key to value
    }

    private fun yamlToList(iter: ListIterator<String>, baseIndent: Int): List<Any> {
        val mutableList:MutableList<Any> = mutableListOf()
        var (key, _) = getNextLine(iter, ":")
        while(true){
            val trimmedKey = key.trim()
            if (trimmedKey == "-")
                mutableList.add(yamlToMap(iter))

            if(!iter.hasNext())
                break

            getNextLine(iter, ":").also {
                key = it.first
            }

            if(baseIndent > countLeadingSpaces(key)){
                iter.previous()
                break
            }
        }
        return mutableList.toList()
    }

    private fun yamlToMap(iter: ListIterator<String>): Map<String, Any> {
        val paramsMap = mutableMapOf<String, Any>()
        var (key, value) = getNextLine(iter, ":")
        val baseIndent: Int = countLeadingSpaces(key)
        while (true){
            val trimmedKey = key.trim()
            if(value.isNotBlank()){
                paramsMap[trimmedKey] = value.trim()
            } else {
                val nextValue = getNextLine(iter, ":").second.also { iter.previous() }
                if(nextValue.isBlank())
                    paramsMap[trimmedKey] = yamlToList(iter, countLeadingSpaces(key))
                else
                    paramsMap[trimmedKey] = yamlToMap(iter)
            }

            if (!iter.hasNext())
                return paramsMap

            getNextLine(iter, ":").also {
                key = it.first
                value = it.second
            }

            if(baseIndent > countLeadingSpaces(key)){
                iter.previous()
                return paramsMap
            }
        }
    }

    final override fun parseObject(yaml: Reader): T {
        val iter = yaml.readLines().filter { it.isNotBlank() }.listIterator()
        return newInstance(yamlToMap(iter))
    }

    @Suppress("UNCHECKED_CAST")
    final override fun parseList(yaml: Reader): List<T> {
        val iter = yaml.readLines().filter { it.isNotBlank() }.listIterator()
        val (key, value) = getNextLine(iter, "-").also {
            iter.previous()
        }

        val mutableList: MutableList<T> = mutableListOf()
        if(value.isBlank())
            while(iter.hasNext())
                mutableList += yamlToList(iter, countLeadingSpaces(key)).map { newInstance(it as Map<String, Any>) }
        else
            while(iter.hasNext())
                mutableList += primitiveMap[type]!!(getNextLine(iter, "-").second.trim()) as T
        return mutableList
    }
}
