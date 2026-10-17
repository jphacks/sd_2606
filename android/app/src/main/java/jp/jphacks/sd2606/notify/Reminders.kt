package jp.jphacks.sd2606.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import jp.jphacks.sd2606.MainActivity
import jp.jphacks.sd2606.R
import jp.jphacks.sd2606.data.LocalStore
import jp.jphacks.sd2606.data.ScheduledNotification

/**
 * 期限の前の通知を端末の中だけで予約する（AlarmManager）。サーバーは使わない。
 * 予約した内容は端末に保存し、再起動したら BootReceiver が予約し直す。
 */
object Reminders {
    private const val CHANNEL_ID = "reminders"
    private const val ACTION_NOTIFY = "jp.jphacks.sd2606.action.REMINDER"

    /** 前に予約したものを取り消し、items を予約し直す。 */
    fun sync(context: Context, items: List<ScheduledNotification>) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val store = LocalStore(context)
        store.loadScheduled().forEach { alarm.cancel(pendingIntent(context, it)) }
        // 正確な時刻でなくてよいので、権限の要らない setAndAllowWhileIdle を使う
        items.forEach { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.atMillis, pendingIntent(context, it)) }
        store.saveScheduled(items)
    }

    /** 再起動したとき: 保存してある予約のうち、まだ来ていないものを予約し直す。 */
    fun restore(context: Context) {
        val now = System.currentTimeMillis()
        sync(context, LocalStore(context).loadScheduled().filter { it.atMillis > now })
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(NotificationChannel(CHANNEL_ID, "期限の通知", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun pendingIntent(context: Context, n: ScheduledNotification): PendingIntent {
        // data を通知ごとに変えて、取り消すときに同じ予約を指せるようにする
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_NOTIFY)
            .setData(Uri.parse("sd2606://reminder/" + Uri.encode(n.key)))
            .putExtra(EXTRA_TITLE, n.title)
            .putExtra(EXTRA_TEXT, n.text)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal const val EXTRA_TITLE = "title"
    internal const val EXTRA_TEXT = "text"
}

/** 予約した時刻に通知を出す。 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(Reminders.EXTRA_TITLE) ?: return
        val text = intent.getStringExtra(Reminders.EXTRA_TEXT).orEmpty()
        Reminders.show(context, intent.dataString.hashCode(), title, text)
    }
}

/** 再起動すると予約が消えるので、予約し直す。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.restore(context)
    }
}
