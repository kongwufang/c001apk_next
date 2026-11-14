package com.example.c001apk.util

import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import org.junit.Assert.*
import org.junit.Test

class LocalDuidProtocolTest {
    private val ssaid = "0123456789abcdef"
    private val fixtureId = "DU" + "a".repeat(34)

    private fun fixture(name: String) = requireNotNull(javaClass.getResourceAsStream("/szlm/$name"))
        .use { it.readBytes() }

    @Test fun matchesIndependentPythonFingerprintAndWirePayload() {
        // Fixed oracle generated with Python hmac + the upstream getduid.py dump_json/encode_body.
        val expected = JsonParser.parseString(fixture("fingerprint.json").toString(Charsets.UTF_8)).asJsonObject
        val actual = LocalDuidProtocol.fingerprint(ssaid)
        expected.entrySet().forEach { (key, value) -> assertEquals(key, value.asString, actual[key]) }
        val template = File("src/main/res/raw/szlm_template.json").readText(Charsets.UTF_8)
        val body = LocalDuidProtocol.encode(LocalDuidProtocol.payload(template, ssaid))
        val inflated = InflaterInputStream(xor(body).inputStream()).use { it.readBytes() }
        assertArrayEquals(fixture("payload.json"), inflated)
        val json = JsonParser.parseString(inflated.toString(Charsets.UTF_8)).asJsonObject
        assertTrue(json["7S2"].asJsonObject["0"].isJsonObject)
        assertEquals("酷安", json["h6Z"].asString)
        assertTrue(json["SAN"].asJsonPrimitive.isNumber)
    }

    @Test fun stableForSameSsaidAndAllSixFieldsChangeForAnotherSsaid() {
        val first = LocalDuidProtocol.fingerprint(ssaid)
        assertEquals(first, LocalDuidProtocol.fingerprint(" 0123456789ABCDEF "))
        assertEquals(first, LocalDuidProtocol.fingerprint(ssaid))
        val other = LocalDuidProtocol.fingerprint("fedcba9876543210")
        first.keys.forEach { assertNotEquals(it, first[it], other[it]) }
        assertTrue(first.getValue("sfOo").matches(Regex("[A-F0-9]{7}")))
        assertTrue(first.getValue("2cO").matches(Regex("[0-9]{10}\\.[0-9]{9}")))
    }

    @Test fun refusesMissingSharedAndMalformedSsaid() {
        listOf("", "null", "0", "0000000000000000", "9774d56d682e549c", "not-a-device-id!")
            .forEach { invalid ->
                assertThrows(IllegalArgumentException::class.java) { LocalDuidProtocol.fingerprint(invalid) }
            }
    }

    @Test fun acceptsBothPythonResponseForms() {
        assertEquals(fixtureId, LocalDuidProtocol.decode(Base64.getMimeDecoder().decode(fixture("response-zlib.b64"))))
        assertEquals(fixtureId, LocalDuidProtocol.decode(Base64.getMimeDecoder().decode(fixture("response-json.b64"))))
    }

    @Test fun rejectsZeroMissingWrongTypeMalformedAndOversizedResponses() {
        listOf(
            "{\"n_a\":\"${"0".repeat(36)}\"}",
            "{\"n_a\":\"DU${"0".repeat(34)}\"}",
            "{}", "{\"n_a\":7}", "{\"n_a\":null}", "{\"n_a\":\"bad\"}", "not-json",
        ).forEach { json ->
            assertThrows(IOException::class.java) { LocalDuidProtocol.decode(xor(json.toByteArray())) }
        }
        assertThrows(IOException::class.java) { LocalDuidProtocol.decode(byteArrayOf()) }
        assertThrows(IOException::class.java) { LocalDuidProtocol.decode(ByteArray(65537)) }
        val huge = ByteArrayOutputStream().apply {
            DeflaterOutputStream(this).use { it.write(ByteArray(100000) { 32 }) }
        }.toByteArray()
        assertThrows(IOException::class.java) { LocalDuidProtocol.decode(xor(huge)) }
        assertThrows(IOException::class.java) {
            LocalDuidProtocol.readBounded(ByteArray(65537).inputStream())
        }
    }

    private fun xor(bytes: ByteArray): ByteArray {
        val key = "xp7j@&!v5]2k#+3z{n9\$q^r%[8f1*d4e:c>0l?m/6)yb-=w}".toByteArray()
        return ByteArray(bytes.size) { (bytes[it].toInt() xor key[it % key.size].toInt()).toByte() }
    }
}
