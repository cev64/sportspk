package com.personal.sportspk.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.personal.sportspk.MainActivity
import com.personal.sportspk.R
import com.personal.sportspk.domain.model.Game

/**
 * Local notifications for favorite-team games.
 *
 * Two events only - kickoff and final. Play-by-play alerts are deliberately not implemented:
 * the spec calls for glanceable check-ins, not a stream of interruptions.
 *
 * Each category has its own channel so they can be silenced independently from Android's
 * settings without turning the other one off.
 */
class GameNotifier(private val context: Context) {

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GAME_START,
                context.getString(R.string.notif_channel_game_start_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_game_start_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FINAL,
                context.getString(R.string.notif_channel_final_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_final_desc) }
        )
    }

    fun canPostNotifications(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifyGameStarted(game: Game) {
        post(
            channelId = CHANNEL_GAME_START,
            notificationId = game.id.notificationId(START_SALT),
            title = "${game.league.shortName} · Now playing",
            text = "${game.away.team.displayName} at ${game.home.team.displayName}",
            game = game,
        )
    }

    fun notifyFinalScore(game: Game) {
        val away = "${game.away.team.abbreviation} ${game.away.score ?: 0}"
        val home = "${game.home.team.abbreviation} ${game.home.score ?: 0}"
        post(
            channelId = CHANNEL_FINAL,
            notificationId = game.id.notificationId(FINAL_SALT),
            title = "${game.league.shortName} · Final",
            text = "$away, $home",
            game = game,
        )
    }

    private fun post(
        channelId: String,
        notificationId: Int,
        title: String,
        text: String,
        game: Game,
    ) {
        // Checked inline (not just via canPostNotifications) so lint can see the guard on the
        // notify() call itself. POST_NOTIFICATIONS is user-revocable at any time.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(deepLinkIntent(game))
            .build()

        // Permission can still be revoked between the check above and this call.
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission was revoked before posting", e)
        }
    }

    /** Opens the app straight at this game's detail screen rather than the feed. */
    private fun deepLinkIntent(game: Game): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, gameDeepLink(game)).apply {
            setClass(context, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            context,
            game.id.notificationId(DEEP_LINK_SALT),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "GameNotifier"

        const val CHANNEL_GAME_START = "game_start"
        const val CHANNEL_FINAL = "final_score"

        private const val START_SALT = 1
        private const val FINAL_SALT = 2
        private const val DEEP_LINK_SALT = 3

        /** sportspk://game/{leagueId}/{gameId} */
        fun gameDeepLink(game: Game): Uri =
            Uri.parse("sportspk://game/${game.league.id}/${game.id}")

        private fun String.notificationId(salt: Int): Int = (hashCode() * 31 + salt)
    }
}
