package io.rebble.libpebblecommon.notification.processor

// App "quirks" are modifications done to the notification content applied before sending to the watch.
object AppQuirks {
    val processTitle = mapOf(

        // Twitter: DMs titles are: "Sender: Sender". We want to show only one instance of sender name only.
        "com.twitter.android" to fun(title: String, channelName: String): String {
            if (channelName == "Direct Messages") {
                return title.substring(0, title.indexOf(":"))
            }
            return title
        },
    )
}