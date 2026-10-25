package com.example.c001apk.logic.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * 容错的 Gson（2026-10-25 ✓）
 *
 * ## 为什么需要
 * 用户实测：搜索 → 地图 → 切到「应用」tab 报错 ✗
 * ```
 * com.google.gson.JsonSyntaxException:
 *   java.lang.NumberFormatException: For input string: "4.0"
 *   at ...ReflectiveTypeAdapterFactory$1.readIntoField
 *   at ...DataListAdapter.deserialize(HomeFeedResponse.kt:523)
 * ```
 * ⇒ 酷安接口把某个本该是整数的字段下发成 **`"4.0"`**（浮点字符串 ✗）
 * ⇒ 模型里是 `Int` ✗ ⇒ Gson 默认 adapter 直接 `NumberFormatException` ✗
 *
 * ## 怎么办
 * 注册一个 [NumberCoercionFactory] ✓ 让 **Int / Long / Double / Float** 字段
 * 都能吃「数字字符串」✓（`"4.0"` → 4 ✓ `"4"` → 4 ✓ `"abc"` → null ✓ 不炸 ✓）
 *
 * ## 用在哪
 * - `DataListAdapter.plain` / `EntitiesListAdapter.plain`（原来都是裸 `Gson()` ✗）
 * - `GsonConverterFactory.create(...)`（三个 Retrofit 实例）
 *
 * ⚠️ 注意别把 `@field:JsonAdapter` 的类级注册搞进来 ✗
 * 那个会无限递归 ✗（见 `DataListAdapter` 的注释 ✓）
 * 这里**只加 TypeAdapterFactory** ✓ 不碰字段注解 ✓
 */
object LenientGson {

    /** 单例 ✓ Gson 实例线程安全 ✓ */
    val gson: Gson = GsonBuilder()
        .registerTypeAdapterFactory(NumberCoercionFactory)
        .create()

    /**
     * 数字字段容错：字符串形式的数字也接受 ✓
     *
     * - `4`        → 4
     * - `"4"`      → 4
     * - `"4.0"`    → 4（**这是本次的元凶** ✗ 原来会抛 NumberFormatException ✗）
     * - `"abc"`    → 0（读不出来就给 0 ✓ 不炸 ✓；调用方一般只用非空判断 ✓）
     * - `""`/null  → 0
     */
    private object NumberCoercionFactory : TypeAdapterFactory {
        override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            val raw = type.rawType
            if (raw != Int::class.java && raw != Integer::class.java &&
                raw != Long::class.java && raw != java.lang.Long::class.java &&
                raw != Double::class.java && raw != java.lang.Double::class.java &&
                raw != Float::class.java && raw != java.lang.Float::class.java
            ) return null

            val delegate = gson.getDelegateAdapter(this, type)
            return object : TypeAdapter<T>() {
                override fun write(out: JsonWriter, value: T) = delegate.write(out, value)

                @Suppress("UNCHECKED_CAST")
                override fun read(input: JsonReader): T {
                    if (input.peek() != JsonToken.STRING) return delegate.read(input)
                    val s = input.nextString()?.trim().orEmpty()
                    val d = s.toDoubleOrNull()
                    val v: Any = when (raw) {
                        Int::class.java, Integer::class.java -> (d ?: 0.0).toInt()
                        Long::class.java, java.lang.Long::class.java -> (d ?: 0.0).toLong()
                        Float::class.java, java.lang.Float::class.java -> (d ?: 0.0).toFloat()
                        else -> d ?: 0.0
                    }
                    return v as T
                }
            }
        }
    }

    /** 调试用：看一段 json 能不能被解析 */
    fun tryParse(json: String): Boolean = runCatching { gson.fromJson(json, JsonPrimitive::class.java) }.isSuccess
}
