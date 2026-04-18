package com.healthconnectmcp.app

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import java.security.SecureRandom

class MainActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("hcmcp", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!prefs.contains("token")) {
            prefs.edit().putString("token", generateToken()).apply()
        }

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScreen(this)
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
fun HomeScreen(activity: MainActivity) {
    val ctx = activity.applicationContext
    val prefs = ctx.getSharedPreferences("hcmcp", Context.MODE_PRIVATE)

    var port by remember { mutableStateOf(prefs.getInt("port", 8080).toString()) }
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var running by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf<Boolean?>(null) }
    var healthConnectAvailable by remember {
        mutableStateOf(
            HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE
        )
    }

    val permissionContract = PermissionController.createRequestPermissionResultContract()
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = permissionContract
    ) { granted ->
        permissionsGranted = granted.containsAll(HealthReader.REQUIRED_PERMISSIONS)
    }

    LaunchedEffect(Unit) {
        if (healthConnectAvailable) {
            val reader = HealthReader(ctx)
            permissionsGranted = try { reader.hasAllPermissions() } catch (e: Exception) { false }
        }
    }

    val ip = remember {
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        Formatter.formatIpAddress(wifi.connectionInfo.ipAddress)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Health Connect MCP", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Server URL", style = MaterialTheme.typography.titleMedium)
                SelectionText("http://$ip:$port")
                Text("Bearer Token", style = MaterialTheme.typography.titleMedium)
                SelectionText(token)
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

        if (!healthConnectAvailable) {
            Text(
                "Health Connect is not available. Install it from Play Store.",
                color = MaterialTheme.colorScheme.error
            )
        } else if (permissionsGranted == false) {
            Button(
                onClick = { permissionLauncher.launch(HealthReader.REQUIRED_PERMISSIONS) },
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
                },
                enabled = permissionsGranted == true,
                modifier = Modifier.weight(1f)
            ) { Text("Start server") }

            OutlinedButton(
                onClick = {
                    ctx.stopService(Intent(ctx, ServerService::class.java))
                    running = false
                },
                modifier = Modifier.weight(1f)
            ) { Text("Stop server") }
        }

        if (running) {
            Text("Server running", color = MaterialTheme.colorScheme.primary)
        }

        TextButton(onClick = {
            val newToken = ByteArray(24).apply { java.security.SecureRandom().nextBytes(this) }
                .joinToString("") { "%02x".format(it) }
            token = newToken
            prefs.edit().putString("token", newToken).apply()
        }) {
            Text("Regenerate token")
        }
    }
}

@Composable
private fun SelectionText(value: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
