package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var pendingConfigJson: String? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnServiceInternal(pendingConfigJson ?: "")
        } else {
            Toast.makeText(this, "VPN Permission was denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    VlessAppScreen(
                        modifier = Modifier.padding(innerPadding),
                        onConnectClick = { configJson ->
                            connectVpn(configJson)
                        },
                        onDisconnectClick = {
                            disconnectVpn()
                        }
                    )
                }
            }
        }
    }

    private fun connectVpn(configJson: String) {
        pendingConfigJson = configJson
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnServiceInternal(configJson)
        }
    }

    private fun disconnectVpn() {
        val intent = Intent(this, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_DISCONNECT
        }
        startService(intent)
        Toast.makeText(this, "Stopping Xray VPN Service...", Toast.LENGTH_SHORT).show()
    }

    private fun startVpnServiceInternal(configJson: String) {
        val intent = Intent(this, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_CONNECT
            putExtra(XrayVpnService.EXTRA_CONFIG_JSON, configJson)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Starting Xray VPN Service...", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlessAppScreen(
    modifier: Modifier = Modifier,
    onConnectClick: (String) -> Unit = {},
    onDisconnectClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val sampleRealityUri = "vless://a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d@192.168.1.100:443?type=tcp&security=reality&pbk=x9K3mP8nL2vR5qJ7wT1yU4zX6A0bC3dE&fp=chrome&sni=example.com&sid=12345678&flow=xtls-rprx-vision#Sample-VLESS-Reality"
    val sampleWsUri = "vless://99887766-5544-3322-1100-aabbccddeeff@proxy.example.com:443?type=ws&security=tls&path=/vless-ws&sni=proxy.example.com&fp=firefox#Sample-VLESS-WS"
    val sampleGrpcUri = "vless://77665544-3322-1100-9988-ffeeddccbbaa@grpc.example.com:443?type=grpc&security=reality&pbk=x9K3mP8nL2vR5qJ7wT1yU4zX6A0bC3dE&fp=chrome&sni=grpc.example.com&serviceName=vless-grpc#Sample-VLESS-gRPC"

    var inputUri by remember { mutableStateOf(sampleRealityUri) }
    var parsedConfig by remember { mutableStateOf<VlessConfig?>(null) }
    var xrayJsonOutput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVpnConnected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            isVpnConnected = XrayVpnService.instance != null
            delay(1000L)
        }
    }

    fun processUri(uri: String) {
        try {
            errorMessage = null
            val config = VlessParser.parse(uri)
            parsedConfig = config
            xrayJsonOutput = VlessParser.generateXrayJson(config)
        } catch (e: Exception) {
            parsedConfig = null
            xrayJsonOutput = ""
            errorMessage = e.message ?: "Failed to parse VLESS URI"
        }
    }

    LaunchedEffect(Unit) {
        processUri(inputUri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "App Icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = "VLESS Xray Configurator",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Parse VLESS URIs & Manage Xray Core Tunnel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Live Status Banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isVpnConnected) Color(0xFFE6F4EA) else Color(0xFFFCE8E6)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Status Icon",
                        tint = if (isVpnConnected) Color(0xFF137333) else Color(0xFFC5221F)
                    )
                    Column {
                        Text(
                            text = "VPN STATUS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVpnConnected) Color(0xFF137333) else Color(0xFFC5221F),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (isVpnConnected) "CONNECTED & TUNNEL ACTIVE" else "DISCONNECTED",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isVpnConnected) Color(0xFF137333) else Color(0xFFC5221F)
                        )
                    }
                }
            }
        }

        // Presets
        Text(
            text = "Presets:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = {
                    inputUri = sampleRealityUri
                    processUri(sampleRealityUri)
                },
                label = { Text("VLESS + REALITY") },
                modifier = Modifier.testTag("preset_reality_button")
            )
            AssistChip(
                onClick = {
                    inputUri = sampleWsUri
                    processUri(sampleWsUri)
                },
                label = { Text("VLESS + WS") },
                modifier = Modifier.testTag("preset_ws_button")
            )
            AssistChip(
                onClick = {
                    inputUri = sampleGrpcUri
                    processUri(sampleGrpcUri)
                },
                label = { Text("VLESS + gRPC") },
                modifier = Modifier.testTag("preset_grpc_button")
            )
        }

        // URI Input Field
        OutlinedTextField(
            value = inputUri,
            onValueChange = { inputUri = it },
            label = { Text("VLESS URI String") },
            placeholder = { Text("vless://uuid@host:port?...") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vless_uri_input"),
            minLines = 3,
            maxLines = 5
        )

        // Parse Button
        Button(
            onClick = { processUri(inputUri) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("parse_button")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Parse URI & Generate Xray JSON")
        }

        // Connect / Disconnect Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onConnectClick(xrayJsonOutput)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("start_vpn_button"),
                enabled = xrayJsonOutput.isNotEmpty()
            ) {
                Text("Connect")
            }

            OutlinedButton(
                onClick = {
                    onDisconnectClick()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("stop_vpn_button")
            ) {
                Text("Disconnect")
            }
        }

        // Error Banner
        errorMessage?.let { err ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Error: $err",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Primary Protocol Card
        parsedConfig?.let { config ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PROTOCOL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "VLESS",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${config.address}:${config.port}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Parsed Extracted Parameters Card (High Density Grid)
        parsedConfig?.let { config ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "EXTRACTED PARAMETERS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.5.sp
                        )
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        ParamRow("UUID", config.uuid, isMonoValue = true, isBlue = true)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ParamRow("Address", config.address, isMonoValue = true)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ParamRow("Port", config.port.toString(), isMonoValue = true)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ParamRow("Transport Type", config.type)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ParamRow("Security", config.security)
                        if (config.sni.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ParamRow("SNI / ServerName", config.sni)
                        }
                        if (config.fingerprint.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ParamRow("Fingerprint (fp)", config.fingerprint)
                        }
                        if (config.flow.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ParamRow("Flow", config.flow)
                        }
                        if (config.publicKey.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ParamRow("Public Key (pbk)", config.publicKey, isMonoValue = true)
                        }
                        if (config.shortId.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ParamRow("Short ID (sid)", config.shortId, isMonoValue = true)
                        }
                        if (config.remark.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ParamRow("Remark", config.remark)
                        }
                    }
                }
            }
        }

        // Generated Xray JSON Output
        if (xrayJsonOutput.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GENERATED CONFIGURATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80868B),
                            letterSpacing = 1.5.sp
                        )

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Xray Config JSON", xrayJsonOutput)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied JSON to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_json_button")
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy JSON",
                                tint = Color(0xFF80868B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = xrayJsonOutput,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFA5D6FF),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun ParamRow(
    label: String,
    value: String,
    isMonoValue: Boolean = false,
    isBlue: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF44474E)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = if (isMonoValue) FontFamily.Monospace else FontFamily.Default,
            color = if (isBlue) Color(0xFF1A73E8) else Color(0xFF1B1B1F),
            fontWeight = if (isMonoValue || isBlue) FontWeight.Bold else FontWeight.Normal
        )
    }
}

