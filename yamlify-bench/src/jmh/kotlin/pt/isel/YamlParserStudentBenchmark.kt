package pt.isel

import org.openjdk.jmh.annotations.*

@BenchmarkMode(Mode.Throughput)
@State(Scope.Benchmark)
open class YamlParserStudentBenchmark  {

    private val parserStudentBaseline = YamlStudentParser()
    private val parserStudentReflect = YamlParserReflect.yamlParser(Student::class)
    private val parserStudentCojen = YamlParserCojen.yamlParser(Student::class)

    @Benchmark
    fun studentBaseline(): Student {
        return parserStudentBaseline
            .parseObject(yamlStudent.reader())
    }
    @Benchmark
    fun studentReflect(): Student {
        return parserStudentReflect
            .parseObject(yamlStudent.reader())
    }

    @Benchmark
    fun studentCojen(): Student {
        return parserStudentCojen
            .parseObject(yamlStudent.reader())
    }
}

