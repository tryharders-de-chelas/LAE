package pt.isel.test

import pt.isel.YamlArg

class Person(
    val name: String,
    val age: Int,
    val address: Address,
    @YamlArg("Country")
    val from: String
)