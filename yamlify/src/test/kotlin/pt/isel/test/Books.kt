package pt.isel.test;

import pt.isel.YamlConvert
import pt.isel.YamlToDate
import java.time.LocalDate

class Books (val name: String, @YamlConvert(YamlToDate::class) val date:LocalDate)


