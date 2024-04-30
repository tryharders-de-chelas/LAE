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

    private fun getLine(iter: Iterator<String>, delimiter: String): Pair<String, String> {
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

    private fun primitiveList(iter: Iterator<String>,firstLine:String): List<Any> {
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

    private fun objectList(
        iter: Iterator<String>,
        baseIndent: Int,
        current: Pair<String, String>? = null
    ): Pair<List<Any>, Pair<String, String>?> {
        val mutableList:MutableList<Any> = mutableListOf()
        var (key, value) = current ?: getLine(iter, ":")
        do {
            val trimmedKey = key.trim()
            if (trimmedKey == "-") {
                getLine(iter, ":").also {
                    key = it.first
                    value = it.second
                }
                val map = parseToMap(
                    iter,
                    defaultIndent =  countLeadingSpaces(key),
                    current = key to value
                )
                mutableList.add(map.first)
                if (map.second != null){
                    map.second?.let { (k, v) ->
                        key = k
                        value = v
                    }
                    continue
                }
            }

            if(!iter.hasNext())
                break

            getLine(iter, ":").also {
                key = it.first
                value = it.second
            }
        } while (baseIndent <= countLeadingSpaces(key))
        return mutableList.toList() to (key to value)
    }

    private fun parseToMap(
        iter: Iterator<String>,
        defaultIndent: Int? = null,
        current: Pair<String, String>? = null,
    ): Pair<Map<String, Any>, Pair<String, String>?> {
        val paramsMap = mutableMapOf<String, Any>()
        var (key, value) = try {
            current ?: getLine(iter, ":")
        } catch (e: NoSuchElementException) {
            return paramsMap to null
        }
        val baseIndent: Int = defaultIndent ?: countLeadingSpaces(key)
        do {
            val trimmedKey = key.trim()
            if(value.isBlank()){
                if(trimmedKey == "-"){
                    val l = objectList(
                        iter,
                        countLeadingSpaces(key),
                        current = key to value
                    )
                    paramsMap[trimmedKey] = l.first
                    if (l.second != null){
                        l.second?.let { (k, v) ->
                            key = k
                            value = v
                        }
                    }
                    continue
                }
                val next = getLine(iter, ":")
                if(next.second.isBlank()){
                    val l = objectList(
                        iter,
                        countLeadingSpaces(next.first),
                        current = next.first.trim() to next.second.trim()
                    )
                    paramsMap[trimmedKey] = l.first
                    if (l.second != null){
                        l.second?.let { (k, v) ->
                            key = k
                            value = v
                        }
                        continue
                    }
                } else {
                    val map = parseToMap(
                        iter,
                        defaultIndent = countLeadingSpaces(next.first),
                        current = next.first.trim() to next.second.trim()
                    ).also {
                        it.second?.let { (k, v) ->
                            key = k
                            value = v
                        }
                    }
                    paramsMap[trimmedKey] = map.first
                    if(key.isBlank() && value.isBlank()){
                        break
                    }
                    continue
                }
            } else {
                paramsMap[trimmedKey] = value.trim()
            }
            if (iter.hasNext())
                getLine(iter, ":").also {
                    key = it.first
                    value = it.second
                }
            else
                break
        } while (baseIndent <= countLeadingSpaces(key))
        return paramsMap to (key to value)
    }

    final override fun parseObject(yaml: Reader): T {
        yaml.useLines {
            val lines = it.filter { line -> line.isNotEmpty() }.iterator()
            val map = parseToMap(lines).first
            return newInstance(map)
        }
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
                l += objectList(iter, countLeadingSpaces(key)).first.map { newInstance(it as Map<String, Any>) }
            }
            l
        } else {
            @Suppress("UNCHECKED_CAST")
            primitiveList(iter, value).map { primitiveMap[type]!!((it as String).trim()) as T}
        }
    }
}
