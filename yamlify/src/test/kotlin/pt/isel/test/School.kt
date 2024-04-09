package pt.isel.test

import pt.isel.YamlArg

class School(
    val id: Int,
    val name: String,
    @YamlArg("location")
    val address: Address,
    @YamlArg("established")
    val founded: Int,
)