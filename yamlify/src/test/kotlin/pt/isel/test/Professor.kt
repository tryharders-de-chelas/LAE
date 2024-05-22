package pt.isel.test

class Professor(val id: Int, val name: String){
    init {
        counter++
    }

    companion object {
        var counter = 0
            private set
    }
}