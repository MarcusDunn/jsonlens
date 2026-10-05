package ca.marcusdunn.jsonlens.kotlinx

import ca.marcusdunn.jsonlens.model.JsonString
import ca.marcusdunn.jsonlens.model.MemberCursor

/**
 * A cursor over the entries of a map with `String` keys: no stream and no `Property` for each
 * member.
 *
 * @param beforeFirst the value before the first call of [next], which a caller never reads
 */
internal class EntryCursor<N : Any>(private val entries: Iterator<Map.Entry<String, N>>, beforeFirst: N) : MemberCursor<N> {

    private var name = ""
    private var current = beforeFirst

    override fun next(): Boolean {
        if (!entries.hasNext()) {
            return false
        }
        val entry = entries.next()
        name = entry.key
        current = entry.value
        return true
    }

    override fun name(): JsonString = JsonString.of(name)

    override fun value(): N = current
}
