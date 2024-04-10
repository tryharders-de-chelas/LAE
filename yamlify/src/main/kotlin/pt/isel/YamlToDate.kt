package pt.isel

import java.time.LocalDate

class YamlToDate{
    fun convertYamlToObject(yaml: String) : LocalDate {
        val words = yaml.split("-")
        return LocalDate.of(words[0].toInt(), words[1].toInt(), words[2].toInt())
    }
}
