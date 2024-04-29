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

    private fun getLine(iter: ListIterator<String>, delimiter: String): Pair<String, String> {
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

    private fun primitiveList(iter: ListIterator<String>,firstLine:String): List<Any> {
        val mutableList:MutableList<Any> = mutableListOf()
        val baseIndent: Int = countLeadingSpaces(firstLine)
        var value: String
        while(true){
            value = getLine(iter, "-").second
            mutableList.add(value)
            if(baseIndent != countLeadingSpaces(value) || !iter.hasNext()) break
        }
        return mutableList
    }

    private fun objectList(iter: ListIterator<String>, baseIndent: Int): List<Any> {
        val mutableList:MutableList<Any> = mutableListOf()
        var (key, value) = getLine(iter, ":")
        while(true){
            val trimmedKey = key.trim()
            if (trimmedKey == "-") {
                mutableList.add(parseToMap(iter))
            }

            if(!iter.hasNext()) {
                break
            }

            getLine(iter, ":").also {
                key = it.first
                value = it.second
            }
            if(baseIndent >= countLeadingSpaces(key)){
                iter.previous()
                break
            }
        }
        return mutableList.toList()
    }

    private fun parseToMap(iter: ListIterator<String>,  defaultIndent: Int? = null): Map<String, Any> {
        val paramsMap = mutableMapOf<String, Any>()
        var (key, value) = getLine(iter, ":")
        val baseIndent: Int = defaultIndent ?: countLeadingSpaces(key)
        while (true){
            val trimmedKey = key.trim()
            if(value.isBlank()){
                val nextValue = getLine(iter, ":").second.also { iter.previous() }
                if(nextValue.isBlank())
                    paramsMap[trimmedKey] = objectList(iter, countLeadingSpaces(key))
                else
                    paramsMap[trimmedKey] = parseToMap(iter)
            } else {
                paramsMap[trimmedKey] = value.trim()
            }

            if (iter.hasNext())
                getLine(iter, ":").also {
                    key = it.first
                    value = it.second
                }
            else
                return paramsMap
            if(baseIndent > countLeadingSpaces(key)){
                iter.previous()
                return paramsMap
            }
        }
    }

    final override fun parseObject(yaml: Reader): T {
        val iter = yaml.readText().trimIndent().lines().listIterator()
        return newInstance(parseToMap(iter))
    }

    final override fun parseList(yaml: Reader): List<T> {
        val primitiveMap: Map<KClass<*>, (Any) -> Any> =
            mapOf(
                Boolean::class to { v: Any -> (v as String).toBoolean() },
                Char::class to { v: Any -> (v as String).first() },
                Short::class to { v: Any -> (v as String).toShort() },
                Int::class to { v: Any -> (v as String).toInt() },
                Long::class to { v: Any -> (v as String).toLong() },
                Float::class to { v: Any -> (v as String).toFloat() },
                Double::class to { v: Any -> (v as String).toDouble() },
                String::class to { v: Any -> (v as String).trim() }
            )

        val iter = yaml.readText().trimIndent().lines().listIterator()
        val (key, value) = getLine(iter, "-").also {
            iter.previous()
        }

        return if(value.isBlank()){
            val l = mutableListOf<T>()
            while(iter.hasNext()){
                @Suppress("UNCHECKED_CAST")
                l += objectList(iter, countLeadingSpaces(key)).map { newInstance(it as Map<String, Any>) }
            }
            l
        } else {
            @Suppress("UNCHECKED_CAST")
            primitiveList(iter, value).map { primitiveMap[type]!!((it as String).trim()) as T}
        }
    }
}
