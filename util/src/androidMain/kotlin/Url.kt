//import android.net.Uri
//import co.touchlab.kermit.Logger
//import io.ktor.http.Url
//
//fun Uri.toKtorUrl(): Url? {
//    return try {
//        Url(toString())
//    } catch (e: IllegalArgumentException) {
//        Logger.w("Couldn't convert $this to Url")
//        null
//    }
//}