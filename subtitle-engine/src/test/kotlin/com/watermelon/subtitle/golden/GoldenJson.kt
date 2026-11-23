package com.watermelon.subtitle.golden

import java.io.File

/**
 * Minimal deterministic JSON writer for the migration golden corpus.
 *
 * Every floating-point value is emitted twice: a decimal string and the raw IEEE-754 bit
 * pattern. That lets the owner's tolerance decision (D-3) be applied without regenerating the
 * corpus. No dependency beyond the JDK is used.
 */
internal object GoldenJson {

    const val GENERATE_PROPERTY = "watermelon.generateGolden"
    const val DIR_PROPERTY = "watermelon.goldenDir"

    fun dir(): File {
        val path = System.getProperty(DIR_PROPERTY)
            ?: error("Set -D$DIR_PROPERTY=<absolute directory>")
        val dir = File(path)
        if (!dir.isDirectory && !dir.mkdirs()) error("Cannot create golden directory: $dir")
        return dir
    }

    fun enabled(): Boolean = System.getProperty(GENERATE_PROPERTY) == "true"

    fun write(dir: File, name: String, body: String) {
        File(dir, "$name.json").writeText(body.trimIndent() + "\n", Charsets.UTF_8)
    }

    fun q(value: String): String = buildString(value.length + 2) {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
    }

    fun str(value: String): String = q(value)
    fun nullableStr(value: String?): String = if (value == null) "null" else q(value)
    fun num(value: Long): String = value.toString()
    fun num(value: Int): String = value.toString()
    fun bool(value: Boolean): String = value.toString()

    fun obj(vararg fields: Pair<String, String>): String =
        fields.joinToString(prefix = "{", postfix = "}") { (key, value) -> "${q(key)}:$value" }

    fun arr(values: List<String>): String = values.joinToString(prefix = "[", postfix = "]")

    fun f32(value: Float): String {
        val raw = java.lang.Float.floatToRawIntBits(value)
        return obj(
            "decimal" to str(value.toString()),
            "bits" to str(java.lang.Integer.toHexString(raw).padStart(8, '0')),
        )
    }

    fun f64(value: Double): String {
        val raw = java.lang.Double.doubleToRawLongBits(value)
        return obj(
            "decimal" to str(value.toString()),
            "bits" to str(java.lang.Long.toHexString(raw).padStart(16, '0')),
        )
    }

    fun floats(values: FloatArray): String = arr(values.map(::f32))

    fun case(id: String, input: String, output: String): String =
        obj("id" to str(id), "input" to input, "output" to output)

    fun file(component: String, cases: List<String>): String = obj(
        "component" to str(component),
        "generator" to str("kotlin"),
        "cases" to arr(cases.sortedBy { it.substringAfter("\"id\":\"").substringBefore("\"") }),
    )
}
