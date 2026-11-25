package com.goepub.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goepub.app.ui.main.MainScreen
import com.goepub.app.ui.main.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge sesuai standar Android 15+ & Play Store
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.factory(applicationContext)
            )

            // Pemilih dokumen resmi SAF (tanpa izin penyimpanan luas)
            val pickEpubLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    mainViewModel.onFilePicked(uri)
                }
            }

            // Izin notifikasi opsional (Android 13+) untuk menampilkan progres latar belakang
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* Opsional: proses WorkManager tetap berjalan meskipun pengguna menolak */ }

            MainScreen(
                viewModel = mainViewModel,
                onPickFile = {
                    pickEpubLauncher.launch(
                        arrayOf(
                            "application/epub+zip",
                            "application/octet-stream"
                        )
                    )
                },
                onRequestNotificationPermissionIfNeeded = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            )
        }
    }
}
