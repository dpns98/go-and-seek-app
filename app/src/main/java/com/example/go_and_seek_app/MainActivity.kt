package com.example.go_and_seek_app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.go_and_seek_app.ui.theme.GoandseekappTheme
import com.example.go_and_seek_app.ui.viewmodel.LocationViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoandseekappTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: LocationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var locationPermissionGranted by remember { mutableStateOf(false) }
    var showFoundDialog by remember { mutableStateOf(false) }
    var playAgainTrigger by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            locationPermissionGranted = true
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    LaunchedEffect(locationPermissionGranted, playAgainTrigger) {
        if (!locationPermissionGranted) return@LaunchedEffect
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        // Make the initial request once
        val initialLocation = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        if (initialLocation != null) {
            viewModel.fetchInitialLocation(initialLocation.latitude, initialLocation.longitude)
        }
        // Then every 5s only update device location and recalculate distance
        while (true) {
            delay(5_000L)
            val location = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            if (location != null) {
                viewModel.updateDeviceLocation(location.latitude, location.longitude)
            }
        }
    }

    // Show congratulations dialog when distance drops below 10m
    val distance = uiState.distanceMeters
    if (distance != null && distance < 10f && !uiState.isLoading && uiState.imageBase64 != null && !showFoundDialog) {
        showFoundDialog = true
    }

    if (showFoundDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Congratulations!") },
            text = { Text("You have found the location! Do you want to play again?") },
            confirmButton = {
                Button(onClick = {
                    showFoundDialog = false
                    viewModel.resetState()
                    playAgainTrigger++
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    (context as? Activity)?.finish()
                }) {
                    Text("No")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Street View image from API response
        when {
            uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.size(64.dp))
            uiState.imageBase64 != null -> StreetViewImage(base64 = uiState.imageBase64!!)
            uiState.error != null -> Text(text = "Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Distance: ${uiState.distanceMeters?.let { "%.0f m".format(it) } ?: "-"}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
    }
}

@Composable
fun StreetViewImage(base64: String) {
    val bitmap = remember(base64) {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Street View Image",
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            contentScale = ContentScale.Crop
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    GoandseekappTheme {
        MainScreen()
    }
}
