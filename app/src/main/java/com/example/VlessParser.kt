package com.example

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

data class VlessConfig(
    val uuid: String,
    val address: String,
    val port: Int,
    val type: String = "tcp",
    val security: String = "none",
    val sni: String = "",
    val fingerprint: String = "",
    val flow: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val remark: String = "",
    val path: String = "",
    val serviceName: String = "",
    val spiderX: String = ""
)

object VlessParser {

    fun parse(rawUri: String): VlessConfig {
        val trimmed = rawUri.trim()
        if (!trimmed.startsWith("vless://", ignoreCase = true)) {
            throw IllegalArgumentException("Invalid VLESS URI: Must start with 'vless://'")
        }

        // Split URI into main body and optional remark/fragment (#)
        val fragmentParts = trimmed.split("#", limit = 2)
        val uriWithoutFragment = fragmentParts[0]
        val remark = if (fragmentParts.size > 1) {
            decodeUrlComponent(fragmentParts[1])
        } else {
            ""
        }

        // Strip "vless://" prefix
        val mainContent = uriWithoutFragment.substring("vless://".length)

        // Split userInfo (UUID) and host details by '@'
        val atParts = mainContent.split("@", limit = 2)
        if (atParts.size < 2) {
            throw IllegalArgumentException("Invalid VLESS URI: Missing '@' user info separator")
        }

        val uuid = decodeUrlComponent(atParts[0])
        val hostAndQueryParams = atParts[1]

        // Split host:port and query parameters by '?'
        val queryParts = hostAndQueryParams.split("?", limit = 2)
        val hostPortStr = queryParts[0]
        val queryString = if (queryParts.size > 1) queryParts[1] else ""

        // Extract host address and port
        val (address, port) = parseHostAndPort(hostPortStr)

        // Extract query map
        val queryParams = parseQueryParams(queryString)

        val type = queryParams["type"] ?: queryParams["network"] ?: "tcp"
        val pbk = queryParams["pbk"] ?: queryParams["publicKey"] ?: ""
        val security = queryParams["security"] ?: if (pbk.isNotEmpty()) "reality" else "none"
        val sni = queryParams["sni"] ?: queryParams["serverName"] ?: queryParams["host"] ?: ""
        val fp = queryParams["fp"] ?: queryParams["fingerprint"] ?: ""
        val flow = queryParams["flow"] ?: ""
        val sid = queryParams["sid"] ?: queryParams["shortId"] ?: ""
        val path = queryParams["path"] ?: "/"
        val serviceName = queryParams["serviceName"] ?: ""
        val spiderX = queryParams["spx"] ?: ""

        return VlessConfig(
            uuid = uuid,
            address = address,
            port = port,
            type = type,
            security = security,
            sni = sni,
            fingerprint = fp,
            flow = flow,
            publicKey = pbk,
            shortId = sid,
            remark = remark,
            path = path,
            serviceName = serviceName,
            spiderX = spiderX
        )
    }

    private fun parseHostAndPort(hostPortStr: String): Pair<String, Int> {
        return if (hostPortStr.startsWith("[")) {
            val closeBracketIndex = hostPortStr.indexOf("]")
            if (closeBracketIndex != -1) {
                val host = hostPortStr.substring(1, closeBracketIndex)
                val portStr = if (hostPortStr.length > closeBracketIndex + 1 && hostPortStr[closeBracketIndex + 1] == ':') {
                    hostPortStr.substring(closeBracketIndex + 2)
                } else {
                    "443"
                }
                Pair(host, portStr.toIntOrNull() ?: 443)
            } else {
                Pair(hostPortStr, 443)
            }
        } else {
            val lastColonIndex = hostPortStr.lastIndexOf(":")
            if (lastColonIndex != -1) {
                val host = hostPortStr.substring(0, lastColonIndex)
                val portStr = hostPortStr.substring(lastColonIndex + 1)
                Pair(host, portStr.toIntOrNull() ?: 443)
            } else {
                Pair(hostPortStr, 443)
            }
        }
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        if (queryString.isEmpty()) return emptyMap()
        val params = mutableMapOf<String, String>()
        val pairs = queryString.split("&")
        for (pair in pairs) {
            if (pair.isEmpty()) continue
            val keyValue = pair.split("=", limit = 2)
            val key = decodeUrlComponent(keyValue[0])
            val value = if (keyValue.size > 1) decodeUrlComponent(keyValue[1]) else ""
            params[key] = value
        }
        return params
    }

    private fun decodeUrlComponent(value: String): String {
        return try {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            value
        }
    }

    fun generateXrayJson(config: VlessConfig): String {
        val root = JSONObject()

        val log = JSONObject().apply {
            put("loglevel", "warning")
        }
        root.put("log", log)

        val inbounds = JSONArray().apply {
            val socksInbound = JSONObject().apply {
                put("tag", "socks-in")
                put("port", 10808)
                put("listen", "127.0.0.1")
                put("protocol", "socks")
                put("settings", JSONObject().apply {
                    put("udp", true)
                    put("auth", "noauth")
                })
            }
            val httpInbound = JSONObject().apply {
                put("tag", "http-in")
                put("port", 10809)
                put("listen", "127.0.0.1")
                put("protocol", "http")
            }
            put(socksInbound)
            put(httpInbound)
        }
        root.put("inbounds", inbounds)

        val outbounds = JSONArray()

        val vlessOutbound = JSONObject()
        vlessOutbound.put("tag", "proxy")
        vlessOutbound.put("protocol", "vless")

        val vlessSettings = JSONObject()
        val vnextList = JSONArray()
        val vnextObj = JSONObject()
        vnextObj.put("address", config.address)
        vnextObj.put("port", config.port)

        val usersList = JSONArray()
        val userObj = JSONObject()
        userObj.put("id", config.uuid)
        userObj.put("encryption", "none")
        if (config.flow.isNotEmpty()) {
            userObj.put("flow", config.flow)
        }
        usersList.put(userObj)

        vnextObj.put("users", usersList)
        vnextList.put(vnextObj)
        vlessSettings.put("vnext", vnextList)
        vlessOutbound.put("settings", vlessSettings)

        val streamSettings = JSONObject()
        streamSettings.put("network", if (config.type.isNotEmpty()) config.type else "tcp")

        if (config.security.isNotEmpty() && config.security != "none") {
            streamSettings.put("security", config.security)

            if (config.security.equals("reality", ignoreCase = true)) {
                val realitySettings = JSONObject()
                if (config.sni.isNotEmpty()) {
                    realitySettings.put("serverName", config.sni)
                }
                if (config.fingerprint.isNotEmpty()) {
                    realitySettings.put("fingerprint", config.fingerprint)
                }
                if (config.publicKey.isNotEmpty()) {
                    realitySettings.put("publicKey", config.publicKey)
                }
                if (config.shortId.isNotEmpty()) {
                    realitySettings.put("shortId", config.shortId)
                }
                if (config.spiderX.isNotEmpty()) {
                    realitySettings.put("spiderX", config.spiderX)
                }
                streamSettings.put("realitySettings", realitySettings)
            } else if (config.security.equals("tls", ignoreCase = true)) {
                val tlsSettings = JSONObject()
                if (config.sni.isNotEmpty()) {
                    tlsSettings.put("serverName", config.sni)
                }
                if (config.fingerprint.isNotEmpty()) {
                    tlsSettings.put("fingerprint", config.fingerprint)
                }
                streamSettings.put("tlsSettings", tlsSettings)
            }
        }

        when (config.type.lowercase()) {
            "ws" -> {
                val wsSettings = JSONObject()
                if (config.path.isNotEmpty()) {
                    wsSettings.put("path", config.path)
                }
                if (config.sni.isNotEmpty()) {
                    val headers = JSONObject()
                    headers.put("Host", config.sni)
                    wsSettings.put("headers", headers)
                }
                streamSettings.put("wsSettings", wsSettings)
            }
            "grpc" -> {
                val grpcSettings = JSONObject()
                if (config.serviceName.isNotEmpty()) {
                    grpcSettings.put("serviceName", config.serviceName)
                }
                grpcSettings.put("multiMode", false)
                streamSettings.put("grpcSettings", grpcSettings)
            }
        }

        vlessOutbound.put("streamSettings", streamSettings)
        outbounds.put(vlessOutbound)

        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        }
        val blockOutbound = JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        }
        outbounds.put(directOutbound)
        outbounds.put(blockOutbound)

        root.put("outbounds", outbounds)

        return root.toString(2)
    }

    fun parseAndGenerateJson(rawUri: String): String {
        val config = parse(rawUri)
        return generateXrayJson(config)
    }
}
