package org.introskipper.segmenteditor.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.introskipper.segmenteditor.ui.theme.ReactInMobileTheme
import java.util.Locale

class VideoPlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val videoUrl = intent.getStringExtra("VIDEO_URL") ?: ""
        val itemId = intent.getStringExtra("ITEM_ID") ?: ""
        
        // Validate URL
        if (videoUrl.isBlank()) {
            Toast.makeText(this, "Invalid video URL", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Initialize player
        player = ExoPlayer.Builder(this).build().apply {
            try {
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                playWhenReady = true
            } catch (e: Exception) {
                Log.e("VideoPlayerActivity", "Failed to load video", e)
                Toast.makeText(this@VideoPlayerActivity, "Failed to load video: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        
        setContent {
            ReactInMobileTheme {
                VideoPlayerScreen(
                    player = player,
                    itemId = itemId
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
    
    override fun onPause() {
        super.onPause()
        player?.pause()
    }
}

@Composable
fun VideoPlayerScreen(
    player: ExoPlayer?,
    itemId: String
) {
    val context = LocalContext.current
    var currentPosition by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    
    // Update position every 500ms (more efficient than 100ms)
    LaunchedEffect(player) {
        if (player != null) {
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            })
            
            while (true) {
                currentPosition = player.currentPosition
                kotlinx.coroutines.delay(500)
            }
        }
    }
    
    DisposableEffect(player) {
        onDispose {
            // Player is released by Activity
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Video player
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text("Player not initialized")
            }
        }
        
        // Controls and timestamp display
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Timestamp display
                Text(
                    text = "Current Time: ${formatTime(currentPosition)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Copy timestamp button
                    Button(
                        onClick = {
                            copyTimestampToClipboard(context, currentPosition)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy Timestamp")
                    }
                    
                    // Copy seconds button
                    Button(
                        onClick = {
                            val seconds = (currentPosition / 1000.0).toString()
                            copyToClipboard(context, seconds, "Seconds")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy Seconds")
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Seek backward
                    Button(
                        onClick = {
                            player?.seekTo(maxOf(0, player.currentPosition - 10000))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("-10s")
                    }
                    
                    // Play/Pause
                    Button(
                        onClick = {
                            if (isPlaying) player?.pause() else player?.play()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isPlaying) "Pause" else "Play")
                    }
                    
                    // Seek forward
                    Button(
                        onClick = {
                            player?.seekTo(player.currentPosition + 10000)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+10s")
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

private fun copyTimestampToClipboard(context: Context, milliseconds: Long) {
    val timestamp = formatTime(milliseconds)
    copyToClipboard(context, timestamp, "Timestamp")
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}
