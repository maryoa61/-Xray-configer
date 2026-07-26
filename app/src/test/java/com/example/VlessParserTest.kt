package com.example

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VlessParserTest {

    @Test
    fun testParseRealityVlessUri() {
        val uri = "vless://a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d@192.168.1.100:443?type=tcp&security=reality&pbk=x9K3mP8nL2vR5qJ7wT1yU4zX6A0bC3dE&fp=chrome&sni=example.com&sid=12345678&flow=xtls-rprx-vision#Sample-VLESS-Reality"
        val config = VlessParser.parse(uri)

        assertEquals("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d", config.uuid)
        assertEquals("192.168.1.100", config.address)
        assertEquals(443, config.port)
        assertEquals("tcp", config.type)
        assertEquals("reality", config.security)
        assertEquals("example.com", config.sni)
        assertEquals("chrome", config.fingerprint)
        assertEquals("xtls-rprx-vision", config.flow)
        assertEquals("x9K3mP8nL2vR5qJ7wT1yU4zX6A0bC3dE", config.publicKey)
        assertEquals("12345678", config.shortId)
        assertEquals("Sample-VLESS-Reality", config.remark)

        val json = VlessParser.generateXrayJson(config)
        assertTrue(json.contains("\"protocol\": \"vless\""))
        assertTrue(json.contains("\"id\": \"a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d\""))
        assertTrue(json.contains("\"publicKey\": \"x9K3mP8nL2vR5qJ7wT1yU4zX6A0bC3dE\""))
        assertTrue(json.contains("\"serverName\": \"example.com\""))
        assertTrue(json.contains("\"flow\": \"xtls-rprx-vision\""))
    }
}
