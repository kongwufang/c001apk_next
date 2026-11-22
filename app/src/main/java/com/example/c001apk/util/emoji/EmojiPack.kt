package com.example.c001apk.util.emoji

import org.json.JSONObject

/**
 * Parsed `index.json` of a downloadable emoji pack.
 *
 * Codes keep their surrounding brackets so they match the text the server sends. Group order is
 * the panel tab order; the store returns codes in that order, which is also the pack's display
 * order inside a tab.
 */
internal data class EmojiPack(
    val version: Int,
    val groups: List<Group>,
    val files: Map<String, String>,
) {
    data class Group(val id: String, val title: String, val codes: List<String>)

    val codes: List<String> get() = groups.flatMap { it.codes }

    companion object {
        private val FILE_NAME = Regex("[a-z0-9][a-z0-9_.-]{0,63}\\.webp")

        /** Strict parse: any malformed field, duplicate code or unsafe path rejects the whole pack. */
        fun parse(json: String): EmojiPack? = runCatching {
            val root = JSONObject(json)
            require(root.optInt("schema") == SCHEMA)
            val version = root.getInt("version")
            require(version > 0)
            val array = root.getJSONArray("groups")
            require(array.length() in 1..16)
            val groups = ArrayList<Group>(array.length())
            val codes = LinkedHashSet<String>()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val id = item.getString("id").trim()
                val title = item.getString("title").trim()
                require(id.isNotEmpty() && id.length <= 32 && title.isNotEmpty() && title.length <= 16)
                val list = item.getJSONArray("codes")
                require(list.length() > 0)
                val groupCodes = ArrayList<String>(list.length())
                for (position in 0 until list.length()) {
                    val code = list.getString(position)
                    require(code.startsWith("[") && code.endsWith("]") && code.length in 3..48)
                    require(!code.substring(1, code.lastIndex).contains('['))
                    require(codes.add(code))
                    groupCodes.add(code)
                }
                groups.add(Group(id, title, groupCodes))
            }
            val files = root.getJSONObject("files")
            val paths = HashMap<String, String>(codes.size)
            val keys = files.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                require(code in codes)
                val path = files.getString(code)
                require(path.startsWith("images/") && FILE_NAME.matches(path.removePrefix("images/")))
                paths[code] = path
            }
            require(paths.size == codes.size)
            require(root.optInt("count", codes.size) == codes.size)
            EmojiPack(version, groups, paths)
        }.getOrNull()

        private const val SCHEMA = 1
    }
}
