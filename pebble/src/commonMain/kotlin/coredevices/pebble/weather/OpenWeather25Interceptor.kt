package coredevices.pebble.weather

import androidx.compose.ui.text.intl.Locale
import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import coredevices.pebble.services.PebbleWebServices
import coredevices.util.CoreConfigHolder
import coredevices.util.WeatherUnit
import dev.jordond.compass.geocoder.Geocoder
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.rebble.libpebblecommon.js.HttpInterceptor
import io.rebble.libpebblecommon.js.InterceptResponse
import io.rebble.libpebblecommon.weather.WeatherType
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.Uuid

class OpenWeather25Interceptor(
    private val pebbleWebServices: PebbleWebServices,
    private val clock: Clock,
    private val geocoder: Geocoder,
    private val coreConfigHolder: CoreConfigHolder,
    private val httpClient: HttpClient = HttpClient(),
) : HttpInterceptor {
    private val logger = Logger.withTag("OpenWeather25Interceptor")

    override fun shouldIntercept(url: String): Boolean {
        if (!coreConfigHolder.config.value.interceptPKJSWeather) {
            return false
        }
        return url.asOpenWeather25Request() != null
    }

    override suspend fun onIntercepted(
        url: String,
        method: String,
        body: String?,
        appUuid: Uuid,
    ): InterceptResponse {
        if (!coreConfigHolder.config.value.interceptPKJSWeather) {
            return InterceptResponse.ERROR
        }
        val request = url.asOpenWeather25Request()
        if (request == null) {
            logger.w { "Unknown request: $url" }
            return InterceptResponse.ERROR
        }

        logger.d { "Intercepting weather request" }
        val responseFromOpenWeather = callRealService(request)
        if (responseFromOpenWeather == null) {
            logger.d { "No response from real service" }
            return InterceptResponse.ERROR
        }
        if (responseFromOpenWeather.status.isSuccess()) {
            logger.d { "Success from real service" }
            return InterceptResponse(
                result = responseFromOpenWeather.bodyAsText(),
                status = responseFromOpenWeather.status.value,
            )
        }
        if (responseFromOpenWeather.status.value == 401) {
            logger.d { "401 from real service - calling Pebble service" }
            return callPebbleService(request)
        }
        return InterceptResponse(
            result = "",
            status = responseFromOpenWeather.status.value,
        )
    }

    private fun String.asOpenWeather25Request(): OpenWeather25Request? {
        val uri = Uri.parseOrNull(this.lowercase())
        if (uri?.authority == null) {
            return null
        }
        if (uri.authority != "api.openweathermap.org") {
            return null
        }
        if (uri.path != "/data/2.5/weather") {
            return null
        }
        val lat = uri.getQueryParameter("lat")?.toDoubleOrNull() ?: return null
        val lon = uri.getQueryParameter("lon")?.toDoubleOrNull() ?: return null
        val appId = uri.getQueryParameter("appid") ?: "invalid"
        val mode = uri.getQueryParameter("mode")
        if (mode != null && mode != "json") {
            return null
        }
        val units = uri.getQueryParameter("units")
        val lang = uri.getQueryParameter("lang")
        return OpenWeather25Request(lat = lat, lon = lon, appId = appId, units = units, lang = lang)
    }

    private suspend fun callRealService(request: OpenWeather25Request): HttpResponse? {
        val url = buildString {
            append("https://api.openweathermap.org/data/2.5/weather?lat=${request.lat}&lon=${request.lon}&appid=${request.appId}")
            if (request.units != null) {
                append("&units=${request.units}")
            }
            if (request.lang != null) {
                append("&lang=${request.lang}")
            }
        }
        return try {
            httpClient.get(url)
        } catch (e: IOException) {
            logger.d(e) { "Error fetching from real service" }
            null
        }
    }

    private suspend fun callPebbleService(request: OpenWeather25Request): InterceptResponse {
        val units = request.units.asUnits()
        val weather = pebbleWebServices.getWeather(
            latitude = request.lat,
            longitude = request.lon,
            units = units,
            language = request.lang ?: Locale.current.toLanguageTag(),
        )
        if (weather == null) {
            logger.d { "No response from Pebble service" }
            return InterceptResponse.ERROR
        }
        val location = geocoder.reverse(request.lat, request.lon).getOrNull()?.firstOrNull()
        val placeName = location?.usefulName() ?: "Unknown location"
        val country = location?.isoCountryCode ?: ""
        val temps = weather.conditions.data.observation.tempsFor(units)
        val firstDay = weather.fcstdaily7.data.forecasts.firstOrNull()
        val weatherType = weather.conditions.data.observation.iconCode.toWeatherType()
        val weatherCode = weatherType.toOpenWeatherCode()
        val weatherIcon = weatherType.toOpenWeatherIcon()
        if (temps == null || firstDay == null) {
            logger.d { "No temps/firstDay from Pebble service" }
            return InterceptResponse.ERROR
        }
        val response = OpenWeather25Response(
            coord = OpenWeather25Response.Coord(
                lon = request.lon,
                lat = request.lat,
            ),
            weather = listOf(
                OpenWeather25Response.Weather(
                    id = weatherCode,
                    main = weather.conditions.data.observation.phrase12Char,
                    description = weather.conditions.data.observation.phrase32Char,
                    icon = weatherIcon,
                )
            ),
            base = "stations",
            main = OpenWeather25Response.Main(
                temp = temps.temp.toDouble().maybeKelvin(request.isKelvin),
                feels_like = temps.feelsLike.toDouble().maybeKelvin(request.isKelvin),
                temp_min = temps.min24Hour.toDouble().maybeKelvin(request.isKelvin),
                temp_max = temps.max24Hour.toDouble().maybeKelvin(request.isKelvin),
                pressure = 0, // TODO
                humidity = 0, // TODO
                sea_level = 0, // TODO
                grnd_level = 0, // TODO
            ),
            visibility = 10000, // TODO
            wind = OpenWeather25Response.Wind(
                speed = 0.0, // TODO
                deg = 0, // TODO
                gust = 0.0, // TODO
            ),
            clouds = OpenWeather25Response.Clouds(
                all = 0, // TODO
            ),
            dt = clock.now().epochSeconds,
            sys = OpenWeather25Response.Sys(
                type = 0,
                id = 0,
                country = country,
                sunrise = firstDay.sunrise.epochSeconds,
                sunset = firstDay.sunset.epochSeconds,
            ),
            timezone = 0, // TODO
            id = 0, // TODO
            name = placeName,
            cod = 200,
        )
        logger.d { "Success from Pebble service" }
        return InterceptResponse(
            result = Json.encodeToString(OpenWeather25Response.serializer(), response),
            status = 200,
        )
    }
}

private fun Double.maybeKelvin(kelvin: Boolean) = if (kelvin) {
    this + 273.15
} else {
    this
}

// https://openweathermap.org/weather-conditions?collection=other
private fun WeatherType.toOpenWeatherCode(): Int = when (this) {
    WeatherType.PartlyCloudy -> 802
    WeatherType.CloudyDay -> 804
    WeatherType.LightSnow -> 600
    WeatherType.LightRain -> 500
    WeatherType.HeavyRain -> 502
    WeatherType.HeavySnow -> 602
    WeatherType.Generic -> 800
    WeatherType.Sun -> 800
    WeatherType.RainAndSnow -> 616
    WeatherType.Unknown -> 800
}

private fun WeatherType.toOpenWeatherIcon(): String = when (this) {
    WeatherType.PartlyCloudy -> "03d"
    WeatherType.CloudyDay -> "04d"
    WeatherType.LightSnow -> "13d"
    WeatherType.LightRain -> "10d"
    WeatherType.HeavyRain -> "10d"
    WeatherType.HeavySnow -> "13d"
    WeatherType.Generic -> "01d"
    WeatherType.Sun -> "01d"
    WeatherType.RainAndSnow -> "13d"
    WeatherType.Unknown -> "01d"
}

private data class OpenWeather25Request(
    val lat: Double,
    val lon: Double,
    val appId: String,
    val units: String? = null,
    val lang: String? = null,
) {
    val isKelvin: Boolean = units == null || units == "standard"
}

private fun String?.asUnits(): WeatherUnit = when (this) {
    "imperial" -> WeatherUnit.Imperial
    else -> WeatherUnit.Metric
}

@Serializable
private data class OpenWeather25Response(
    val coord: Coord,
    val weather: List<Weather>,
    val base: String,
    val main: Main,
    val visibility: Int,
    val wind: Wind,
    val clouds: Clouds,
    val dt: Long,
    val sys: Sys,
    val timezone: Int,
    val id: Long,
    val name: String,
    val cod: Int,
) {
    @Serializable
    data class Coord(
        val lon: Double,
        val lat: Double
    )

    @Serializable
    data class Weather(
        val id: Int,
        val main: String,
        val description: String,
        val icon: String
    )

    @Serializable
    data class Main(
        val temp: Double,
        val feels_like: Double,
        val temp_min: Double,
        val temp_max: Double,
        val pressure: Int,
        val humidity: Int,
        val sea_level: Int? = null,
        val grnd_level: Int? = null
    )

    @Serializable
    data class Wind(
        val speed: Double,
        val deg: Int,
        val gust: Double? = null
    )

    @Serializable
    data class Clouds(
        val all: Int
    )

    @Serializable
    data class Sys(
        val type: Int? = null,
        val id: Int? = null,
        val country: String,
        val sunrise: Long,
        val sunset: Long
    )
}
