package org.example.project

import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PlatformFile
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun PreviewApp() {
    App()
}

@Composable
fun App() {
    MaterialTheme {
        var file by remember { mutableStateOf<PlatformFile?>(null) }
        val filePickerLauncher = rememberFilePickerLauncher {
            file = it
        }

        if (file == null) {
            Button(onClick = { filePickerLauncher.launch() }) {
                Text("Open file...")
            }
        } else {
            LaunchedEffect(Unit) {
            }
            Text("$file")
        }
    }
}