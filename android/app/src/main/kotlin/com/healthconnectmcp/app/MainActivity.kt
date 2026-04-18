package com.healthconnectmcp.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.io.File
import java.net.NetworkInterface
import java.security.SecureRandom

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashFile = File(filesDir, "last_crash.txt")
        val previousCrash = if (crashFile.exists()) crashFile.readText() else null
        if (previousCrash != null) crashFile.delete()

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try {
                crashFile.writeText(
                    "${e.javaClass.name}: ${e.message}\n\n${e.stackTraceToString()}"
                )
            } catch (_: Throwable) {}
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        val prefs = getSharedPreferences("hcmcp", Context.MODE_PRIVATE)
        if (!prefs.contains("token")) {
            prefs.edit().putString("token", generateToken()).apply()
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(previousCrash)
                }
            }
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

@Composable
fun HomeScreen(previousCrash: String?) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("hcmcp", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var port by remember { mutableStateOf(prefs.getInt("port", 8080).toString()) }
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var running by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf<Boolean?>(null) }
    var healthConnectStatus by remember { mutableStateOf("checking...") }
    var ipAddress by remember { mutableStateOf(detectLocalIp()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        permissionsGranted = granted.containsAll(HealthReader.REQUIRED_PERMISSIONS)
    }

    LaunchedEffect(Unit) {
        try {
            val status = HealthConnectClient.getSdkStatus(ctx)
            healthConnectStatus = when (status) {
                HealthConnectClient.SDK_AVAILABLE -> "available"
                HealthConnectClient.SDK_UNAVAILABLE -> "unavailable (not supported on this device)"
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update required"
                else -> "unknown ($status)"
            }
            if (status == HealthConnectClient.SDK_AVAILABLE) {
                val reader = HealthReader(ctx)
                permissionsGranted = try {
                    reader.hasAllPermissions()
                } catch (e: Exception) {
                    statusMessage = "Error checking permissions: ${e.message}"
                    false
                }
            }
        } catch (e: Throwable) {
            healthConnectStatus = "error: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Health Connect MCP", style = MaterialTheme.typography.headlineMedium)

        if (previousCrash != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Previous crash",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "May contain details from your Health Connect records. Review before sharing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    SelectionContainer {
                        Text(
                            previousCrash.take(500),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Server URL", style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Text("http://${ipAddress ?: "?"}:$port", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Bearer Token", style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Text(token, style = MaterialTheme.typography.bodyMedium)
                }
                Text("Health Connect: $healthConnectStatus", style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text("Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        if (permissionsGranted == false) {
            Button(
                onClick = {
                    try {
                        permissionLauncher.launch(HealthReader.REQUIRED_PERMISSIONS)
                    } catch (e: Exception) {
                        statusMessage = "Error launching permissions: ${e.message}"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Health Connect permissions")
            }
        } else if (permissionsGranted == true) {
            Text("Permissions granted", color = MaterialTheme.colorScheme.primary)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    try {
                        val portInt = port.toIntOrNull() ?: 8080
                        prefs.edit().putInt("port", portInt).apply()
                        val intent = Intent(ctx, ServerService::class.java).apply {
                            putExtra(ServerService.EXTRA_PORT, portInt)
                            putExtra(ServerService.EXTRA_TOKEN, token)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(ctx, intent)
                        } else {
                            ctx.startService(intent)
                        }
                        running = true
                        statusMessage = "Server started"
                    } catch (e: Exception) {
                        statusMessage = "Error starting server: ${e.message}"
                    }
                },
                enabled = permissionsGranted == true,
                modifier = Modifier.weight(1f)
            ) { Text("Start server") }

            OutlinedButton(
                onClick = {
                    ctx.stopService(Intent(ctx, ServerService::class.java))
                    running = false
                    statusMessage = "Server stopped"
                },
                modifier = Modifier.weight(1f)
            ) { Text("Stop server") }
        }

        if (running) Text("Server running", color = MaterialTheme.colorScheme.primary)
        statusMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        TextButton(onClick = {
            val newToken = ByteArray(24).apply { SecureRandom().nextBytes(this) }
                .joinToString("") { "%02x".format(it) }
            token = newToken
            prefs.edit().putString("token", newToken).apply()

            if (running) {
                try {
                    ctx.stopService(Intent(ctx, ServerService::class.java))
                    val intent = Intent(ctx, ServerService::class.java).apply {
                        putExtra(ServerService.EXTRA_PORT, port.toIntOrNull() ?: 8080)
                        putExtra(ServerService.EXTRA_TOKEN, newToken)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(ctx, intent)
                    } else {
                        ctx.startService(intent)
                    }
                    statusMessage = "Token rotated and server restarted"
                } catch (e: Exception) {
                    statusMessage = "Token rotated; restart failed: ${e.message}"
                }
            } else {
                statusMessage = "Token rotated"
            }
        }) {
            Text("Regenerate token")
        }

        TextButton(onClick = {
            ipAddress = detectLocalIp()
        }) {
            Text("Refresh IP")
        }
    }
}

private fun detectLocalIp(): String? {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (intf in interfaces) {
            if (!intf.isUp || intf.isLoopback) continue
            for (addr in intf.inetAddresses) {
                if (!addr.isLoopbackAddress && addr.hostAddress?.contains('.') == true) {
                    return addr.hostAddress
                }
            }
        }
        null
    } catch (e: Exception) {
        null
    }
}
