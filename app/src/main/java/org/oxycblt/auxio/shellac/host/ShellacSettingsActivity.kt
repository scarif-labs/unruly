package org.oxycblt.auxio.shellac.host

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.music.MusicRepository

/**
 * App-native settings. **Deliberately NOT skinnable** (buildspec 3.7).
 *
 * This Activity never composes a skin. That is the entire point: it stays reachable when the
 * active skin is broken, crashes on launch, or renders an unusable interface. Without it, a
 * bad skin means uninstalling the app to recover.
 *
 * It is exported with its own launcher entry (see AndroidManifest), so it is reachable from
 * the system launcher rather than only from inside a skin that may not be drawing.
 */
@AndroidEntryPoint
class ShellacSettingsActivity : ComponentActivity() {
    @Inject lateinit var registry: SkinRegistry
    @Inject lateinit var musicRepository: MusicRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val active by registry.activeSkin.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxSize().background(Color(0xFF101010)).padding(16.dp)) {
                Text(
                    "Shellac settings",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold)
                Text(
                    "Not skinnable. Reachable even if the active skin is broken.",
                    color = Color(0xFF9E9E9E),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 16.dp))

                Text("Skin", color = Color.White, fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    items(registry.available()) { skin ->
                        val isActive = skin.id == active.id
                        val quarantined = registry.isQuarantined(skin.id)
                        Text(
                            buildString {
                                append(if (isActive) "> " else "  ")
                                append(skin.displayName)
                                if (quarantined) append("  (crashed - will retry)")
                            },
                            color = if (isActive) Color(0xFF8AB4F8) else Color.White,
                            fontSize = 16.sp,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { registry.setActiveSkin(skin.id) }
                                    .padding(vertical = 12.dp))
                    }
                }

                Text("Library", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Rescan music",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { musicRepository.requestIndex(true) }
                            .padding(vertical = 12.dp))
            }
        }
    }
}
