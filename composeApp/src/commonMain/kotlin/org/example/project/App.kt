package org.example.project

import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            Text("$file")
        }
    }
}