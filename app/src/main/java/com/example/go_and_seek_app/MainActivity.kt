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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val configuration = LocalConfiguration.current
    val screenWidthPx = (configuration.screenWidthDp * context.resources.displayMetrics.density).toInt()
    val screenHeightPx = (configuration.screenHeightDp * context.resources.displayMetrics.density).toInt()

    var locationPermissionGranted by remember { mutableStateOf(false) }
    var showFoundDialog by remember { mutableStateOf(false) }
    var playAgainTrigger by remember { mutableStateOf(0) }
    var lastKnownLat by remember { mutableStateOf(0.0) }
    var lastKnownLon by remember { mutableStateOf(0.0) }

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
            lastKnownLat = initialLocation.latitude
            lastKnownLon = initialLocation.longitude
            viewModel.fetchInitialLocation(initialLocation.latitude, initialLocation.longitude, screenWidthPx, screenHeightPx)
        }
        // Then every 3s only update device location and recalculate distance
        while (true) {
            delay(3_000L)
            val location = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            if (location != null) {
                lastKnownLat = location.latitude
                lastKnownLon = location.longitude
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
                    viewModel.onLocationSolved()
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

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.size(64.dp).align(Alignment.Center))
            uiState.imageBase64 != null -> StreetViewImage(base64 = uiState.imageBase64!!)
            uiState.error != null -> Text(
                text = "Error: ${uiState.error}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        uiState.distanceMeters?.let { meters ->
            Text(
                text = "%.0f m".format(meters),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .background(Color(0xFF1565C0), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        Text(
            text = "Score: ${uiState.score}",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color(0xFF1565C0), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        Button(
            onClick = {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        val lat = location?.latitude ?: lastKnownLat
                        val lon = location?.longitude ?: lastKnownLon
                        if (location != null) {
                            lastKnownLat = lat
                            lastKnownLon = lon
                        }
                        viewModel.skipLocation(lat, lon, screenWidthPx, screenHeightPx)
                    }
                    .addOnFailureListener {
                        viewModel.skipLocation(lastKnownLat, lastKnownLon, screenWidthPx, screenHeightPx)
                    }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text(
                text = "Skip",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
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
            modifier = Modifier.fillMaxSize(),
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
