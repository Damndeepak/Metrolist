package com.mochyy.mochymusic.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R

@Composable
internal fun DeeplistLibraryRecovery(failed: Boolean, onRetry: () -> Unit, onClose: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (failed) {
                    Text(stringResource(R.string.deeplist_library_recovery_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.deeplist_library_recovery_body))
                    Button(onClick = onRetry) { Text(stringResource(R.string.deeplist_library_retry)) }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.deeplist_library_close)) }
                } else {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.deeplist_library_preparing))
                }
            }
        }
    }
}
