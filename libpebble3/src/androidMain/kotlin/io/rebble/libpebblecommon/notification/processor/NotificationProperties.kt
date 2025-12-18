package io.rebble.libpebblecommon.notification.processor

import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.timeline.TimelineColor

fun title(sbn: StatusBarNotification, notiApp: NotificationAppItem, channel: ChannelItem?): String {
    return sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE) as String
}
enum class NotificationProperties(
    val pkgName: String,
    val color: TimelineColor?,
    val icon: TimelineIcon?,
    val title: (StatusBarNotification, NotificationAppItem, ChannelItem?) -> String,
) {
    Gmail(pkgName = "com.google.android.gm", color = TimelineColor.Red, icon = TimelineIcon.NotificationGmail, title = title),
    GoogleQuickSearchBox(pkgName = "com.google.android.googlequicksearchbox", color = TimelineColor.BlueMoon, icon = null, title = title),
    Whatsapp(pkgName = "com.whatsapp", color = TimelineColor.IslamicGreen, icon = TimelineIcon.NotificationWhatsapp, title = title),
    GoogleTalk(pkgName = "com.google.android.talk", color = TimelineColor.JaegerGreen, icon = null, title = title),
    AndroidVending(pkgName = "com.android.vending", color = TimelineColor.JaegerGreen, icon = null, title = title),
    FacebookMessenger(pkgName = "com.facebook.orca", color = TimelineColor.BlueMoon, icon = TimelineIcon.NotificationFacebookMessenger, title = title),
    AndroidEmail(pkgName = "com.android.email", color = TimelineColor.Orange, icon = null, title = title),
    GoogleCalendar(pkgName = "com.google.android.calendar", color = TimelineColor.BlueMoon, icon = null, title = title),
    Telegram(pkgName = "org.telegram.messenger", color = TimelineColor.VividCerulean, icon = TimelineIcon.NotificationTelegram, title = title),
    Facebook(pkgName = "com.facebook.katana", color = TimelineColor.CobaltBlue, icon = TimelineIcon.NotificationFacebook, title = title),
    GoogleMessaging(pkgName = "com.google.android.apps.messaging", color = TimelineColor.VividCerulean, icon = TimelineIcon.NotificationGoogleMessenger, title = title),
    Hipchat(pkgName = "com.hipchat", color = TimelineColor.CobaltBlue, icon = null, title = title),
    Skype(pkgName = "com.skype.raider", color = TimelineColor.VividCerulean, icon = null, title = title),
    Twitter(pkgName = "com.twitter.android", color = TimelineColor.VividCerulean, icon = TimelineIcon.NotificationTwitter, title = { sbn, notiApp, channel ->
        val title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE) ?: ""
        if (channel?.name == "Direct Messages") {
            title.substring(0, title.indexOf(":"))
        } else {
            title.toString()
        }
    }),
    Mailbox(pkgName = "com.mailboxapp", color = TimelineColor.VividCerulean, icon = null, title = title),
    Snapchat(pkgName = "com.snapchat.android", color = TimelineColor.Icterine, icon = TimelineIcon.NotificationSnapchat, title = title),
    Wechat(pkgName = "com.tencent.mm", color = TimelineColor.KellyGreen, icon = TimelineIcon.NotificationWeChat, title = title),
    Viber(pkgName = "com.viber.voip", color = TimelineColor.VividViolet, icon = TimelineIcon.NotificationViber, title = title),
    Instagram(pkgName = "com.instagram.android", color = TimelineColor.CobaltBlue, icon = TimelineIcon.NotificationInstagram, title = title),
    Youtube(pkgName = "com.google.android.youtube", color = TimelineColor.Red, icon = TimelineIcon.NotificationYoutube, title = title),
    Kik(pkgName = "kik.android", color = TimelineColor.IslamicGreen, icon = TimelineIcon.NotificationKik, title = title),
    Line(pkgName = "jp.naver.line.android", color = TimelineColor.IslamicGreen, icon = TimelineIcon.NotificationLine, title = title),
    Inbox(pkgName = "com.google.android.apps.inbox", color = TimelineColor.BlueMoon, icon = null, title = title),
    Bbm(pkgName = "com.bbm", color = TimelineColor.DarkGray, icon = null, title = title),
    Outlook(pkgName = "com.microsoft.office.outlook", color = TimelineColor.BlueMoon, icon = TimelineIcon.NotificationOutlook, title = title),
    YahooMail(pkgName = "com.yahoo.mobile.client.android.mail", color = TimelineColor.Indigo, icon = TimelineIcon.NotificationYahooMail, title = title),
    KakaoTalk(pkgName = "com.kakao.talk", color = TimelineColor.Yellow, icon = TimelineIcon.NotificationKakaoTalk, title = title),
    PebbleOg(pkgName = "com.getpebble.android.basalt", color = TimelineColor.Orange, icon = null, title = title),
    Amazon(pkgName = "com.amazon.mshop.android.shopping", color = TimelineColor.ChromeYellow, icon = TimelineIcon.NotificationAmazon, title = title),
    GoogleMaps(pkgName = "com.google.android.apps.maps", color = TimelineColor.BlueMoon, icon = TimelineIcon.NotificationGoogleMaps, title = title),
    GooglePhotos(pkgName = "com.google.android.apps.photos", color = TimelineColor.BlueMoon, icon = TimelineIcon.NotificationGooglePhotos, title = title),
    Linkedin(pkgName = "com.linkedin.android", color = TimelineColor.CobaltBlue, icon = TimelineIcon.NotificationLinkedIn, title = title),
    Slack(pkgName = "com.slack", color = TimelineColor.Folly, icon = TimelineIcon.NotificationSlack, title = title),
    Beeper(pkgName = "com.beeper.android", color = TimelineColor.VividViolet, icon = TimelineIcon.NotificationBeeper, title = title),
    Discord(pkgName = "com.discord", color = TimelineColor.Indigo, icon = TimelineIcon.NotificationDiscord, title = title),
    Bluesky(pkgName = "xyz.blueskyweb.app", color = TimelineColor.VividCerulean, icon = TimelineIcon.NotificationBluesky, title = title),
    Duolingo(pkgName = "com.duolingo", color = TimelineColor.IslamicGreen, icon = TimelineIcon.NotificationDuolingo, title = title),
    Element(pkgName = "im.vector.app", color = TimelineColor.MediumAquamarine, icon = TimelineIcon.NotificationElement, title = title),
    ElementX(pkgName = "io.element.android.x", color = TimelineColor.MediumAquamarine, icon = TimelineIcon.NotificationElement, title = title),
    GoogleChat(pkgName = "com.google.android.apps.dynamite", color = TimelineColor.IslamicGreen, icon = TimelineIcon.NotificationGoogleChat, title = title),
    GoogleTasks(pkgName = "com.google.android.apps.tasks", color = TimelineColor.BlueMoon, icon = TimelineIcon.NotificationGoogleTasks, title = title),
    HomeAssistant(pkgName = "io.homeassistant.companion.android", color = TimelineColor.VividCerulean, icon = TimelineIcon.NotificationHomeAssistant, title = title),
    Steam(pkgName = "com.valvesoftware.android.steam.community", color = TimelineColor.CobaltBlue, icon = TimelineIcon.NotificationSteam, title = title),
    MicrosoftTeams(pkgName = "com.microsoft.teams", color = TimelineColor.Indigo, icon = TimelineIcon.NotificationTeams, title = title),
    Threads(pkgName = "com.instagram.barcelona", color = TimelineColor.DarkGray, icon = TimelineIcon.NotificationThreads, title = title),
    UnifiProtect(pkgName = "com.ubnt.unifi.protect", color = TimelineColor.BlueMoon, icon = TimelineIcon.NotificationUnifiProtect, title = title),
    Zoom(pkgName = "us.zoom.videomeetings", color = TimelineColor.VividCerulean, icon = TimelineIcon.NotificationZoom, title = title),
    Ebay(pkgName = "com.ebay.mobile", color = TimelineColor.Red, icon = TimelineIcon.NotificationEbay, title = title),
    Revolut(pkgName = "com.revolut.revolut", color = TimelineColor.DarkGray, icon = TimelineIcon.PayBill, title = title),
    Wise(pkgName = "com.transferwise.android", color = TimelineColor.Green, icon = TimelineIcon.PayBill, title = title),
    N26(pkgName = "de.number26.android", color = TimelineColor.CadetBlue, icon = TimelineIcon.PayBill, title = title),
    Bunq(pkgName = "com.bunq.android", color = TimelineColor.VividCerulean, icon = TimelineIcon.PayBill, title = title),
    GmailLite(pkgName = "com.google.android.gm.lite", color = null, icon = TimelineIcon.NotificationGmail, title = title),
    TwitterLite(pkgName = "com.twitter.android.lite", color = null, icon = TimelineIcon.NotificationTwitter, title = title),
    TelegramWeb(pkgName = "org.telegram.messenger.web", color = null, icon = TimelineIcon.NotificationTelegram, title = title),
    Challegram(pkgName = "org.thunderdog.challegram", color = null, icon = TimelineIcon.NotificationTelegram, title = title),
    FacebookLite(pkgName = "com.facebook.lite", color = null, icon = TimelineIcon.NotificationFacebook, title = title),
    MicrosoftLync(pkgName = "com.microsoft.office.lync", color = null, icon = TimelineIcon.NotificationSkype, title = title),
    BbmEnterprise(pkgName = "com.bbm.enterprise", color = null, icon = TimelineIcon.NotificationBlackberryMessenger, title = title),
    ProtonMail(pkgName = "ch.protonmail.android", color = null, icon = TimelineIcon.GenericEmail, title = title),
    ProtonCalendar(pkgName = "me.proton.android.calendar", color = null, icon = TimelineIcon.TimelineCalendar, title = title),
    GoogleWallet(pkgName = "com.google.android.apps.walletnfcrel", color = null, icon = TimelineIcon.PayBill, title = title),
    YoutubeReVanced(pkgName = "app.revanced.android.youtube", color = TimelineColor.Red, icon = TimelineIcon.NotificationYoutube, title = title),
    Signal(pkgName = "org.thoughtcrime.securesms", color = TimelineColor.BlueMoon, icon = TimelineIcon.NotificationSignal, title = title),
    Twitch(pkgName = "tv.twitch.android.app", color = TimelineColor.VividViolet, icon = TimelineIcon.NotificationTwitch, title = title),
    ;

    companion object {
        fun lookup(pkgName: String): NotificationProperties? = entries.find { it.pkgName.equals(pkgName, ignoreCase = true) }
    }
}
