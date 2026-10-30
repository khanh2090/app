package com.fitness.companion.presentation.workout

import android.content.Context
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/** Wikimedia asks every client to identify itself with a descriptive User-Agent. */
private const val USER_AGENT = "FitnessCompanion/1.0 (Android; workout guide player)"

/** Creates a player set up for streaming the looping workout guide clips. */
@OptIn(UnstableApi::class)
fun createGuidePlayer(context: Context, videoUrl: String): ExoPlayer {
    val httpFactory = DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)
    val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
        .apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
        }
}

/** Shows [player] in a Media3 `PlayerView`. */
@OptIn(UnstableApi::class)
@Composable
fun GuidePlayerView(player: Player, showControls: Boolean, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = showControls
                // Fills the bars beside clips whose aspect ratio differs from the view's.
                setBackgroundColor(Color.BLACK)
                // A guide is a single clip, so there is no previous or next item to skip to.
                setShowPreviousButton(false)
                setShowNextButton(false)
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

/** Muted, looping preview of an exercise video. Playback stops while the app is not visible. */
@Composable
fun VideoPreview(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(videoUrl) {
        createGuidePlayer(context, videoUrl).apply { volume = 0f }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LifecycleStartEffect(player) {
        player.play()
        onStopOrDispose { player.pause() }
    }
    GuidePlayerView(player = player, showControls = false, modifier = modifier)
}
