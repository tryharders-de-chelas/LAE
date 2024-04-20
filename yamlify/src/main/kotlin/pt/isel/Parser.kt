package pt.isel

interface Parser<T> {
    fun newInstance(args: Map<String, Any>): T
}