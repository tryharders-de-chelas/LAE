package pt.isel

import java.io.File
import java.io.Reader
import kotlin.random.Random
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
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

    private fun yamlToSequence(iter: ListIterator<String>): Sequence<T> {
        return sequence {
            val (_, value) = getNextLine(iter, ":")
            while(true){
                if (value.isBlank()){
                    yield(newInstance(yamlToMap(iter)))
                } else {
                    yield(primitiveMap[type]!!(value) as T)
                }
                if(iter.hasNext()) iter.next() else break
            }
        }
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
                paramsMap[trimmedKey] =
                    if (nextValue.isBlank())
                        yamlToList(iter, countLeadingSpaces(key))
                    else
                        yamlToMap(iter)
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

    final override fun parseList(yaml: Reader): List<T> {
        val iter = yaml.readLines().filter { it.isNotBlank() }.listIterator()
        val (key, value) = getNextLine(iter, "-").also {
            iter.previous()
        }
        val mutableList: MutableList<T> = mutableListOf()
        while(iter.hasNext())
            if(value.isBlank())
                mutableList += yamlToList(iter, countLeadingSpaces(key)).map { newInstance(it as Map<String, Any>) }
            else
                mutableList += primitiveMap[type]!!(getNextLine(iter, "-").second.trim()) as T
        return mutableList
    }

    fun parseSequence(yaml: Reader): Sequence<T> {
        val iter = yaml.readLines().filter { it.isNotBlank() }.listIterator()
        val (k, _) = getNextLine(iter, ":").also { iter.previous() }
        if(k.trim() != "-")
            throw IllegalArgumentException("Invalid YAML format: Expected sequence to start with a '-' but found '${k.trim()}' instead. Please ensure the YAML sequence is correctly formatted.")
        return yamlToSequence(iter)
    }

    fun parseFolderEager(path: String): List<T> {
        val directory = File(path)
        val files = directory.listFiles()
            ?.filter { it.isFile }
            ?.map { file: File ->
                parseObject(file.reader()).also { file.renameTo(File("$directory/${Random.nextLong(Long.MAX_VALUE)}.yaml")) }
            } ?: emptyList()
        return files
    }

    fun parseFolderLazy(path: String): Sequence<T> {
        val directory = File(path)
        val files = directory.listFiles()?.filter { it.isFile }?.iterator() ?: emptyList<File>().iterator()
        return object : Sequence<T>{
            override fun iterator(): Iterator<T> {
                return object : Iterator<T> {
                    override fun hasNext(): Boolean = files.hasNext()
                    override fun next(): T {
                        val file = files.next()
                        return parseObject(file.reader()).also {
                            file.renameTo(File("$directory/${Random.nextLong(Long.MAX_VALUE)}.yaml"))
                        }
                    }
                }
            }
        }
    }
}
