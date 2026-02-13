package coredevices.pebble.weather

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.text.intl.Locale
import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import coredevices.pebble.services.PebbleWebServices
import coredevices.util.CoreConfigHolder
import coredevices.util.WeatherUnit
import dev.jordond.compass.Coordinates
import dev.jordond.compass.Place
import dev.jordond.compass.geocoder.Geocoder
import io.rebble.libpebblecommon.js.HttpInterceptor
import io.rebble.libpebblecommon.js.InterceptResponse
import io.rebble.libpebblecommon.weather.WeatherType
import kotlinx.atomicfu.atomic
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

private val logger = Logger.withTag("YahooWeatherInterceptor")

class YahooWeatherInterceptor(
    private val pebbleWebServices: PebbleWebServices,
    private val geocoder: Geocoder,
    private val coreConfigHolder: CoreConfigHolder,
) : HttpInterceptor {
    private val woeidCache = mutableMapOf<Int, YahooLocation>()
    private val woeidSequence = atomic(0)

    override fun shouldIntercept(url: String): Boolean {
        if (!coreConfigHolder.config.value.interceptPKJSWeather) {
            return false
        }
        return url.asYahooWeatherRequest() != null
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
        val request = url.asYahooWeatherRequest()
        if (request == null) {
            logger.w { "Unknown request: $url" }
            return InterceptResponse.ERROR
        }

        logger.d { "Intercepting weather request - calling Pebble Service" }
        return callPebbleService(request)
    }

    private suspend fun lookupLocation(params: YahooWeatherParams): YahooLocation? {
        val location = when (params) {
            is YahooWeatherParams.LatLon -> {
                val cached = woeidCache.entries.firstOrNull {
                    it.value.coordinates.latitude == params.latitude && it.value.coordinates.longitude == params.longitude
                }
                if (cached != null) return cached.value
                val location =
                    geocoder.reverse(params.latitude, params.longitude).getOrNull()?.firstOrNull()
                        ?: return null
                YahooLocation(
                    coordinates = Coordinates(
                        latitude = params.latitude,
                        longitude = params.longitude
                    ),
                    place = location,
                    woeid = woeidSequence.incrementAndGet(),
                    suppliedName = null,
                )
            }

            is YahooWeatherParams.PlaceName -> {
                val cached = woeidCache.entries.firstOrNull {
                    it.value.suppliedName == params.placeName
                }
                if (cached != null) return cached.value
                val place =
                    geocoder.locations(params.placeName).getOrNull()?.firstOrNull() ?: return null
                val location =
                    geocoder.reverse(place.latitude, place.longitude).getOrNull()?.firstOrNull()
                        ?: return null
                YahooLocation(
                    coordinates = place,
                    place = location,
                    woeid = woeidSequence.incrementAndGet(),
                    suppliedName = params.placeName,
                )
            }

            is YahooWeatherParams.Woeid -> {
                return woeidCache[params.woeid]
            }
        }
        woeidCache[location.woeid] = location
        return location
    }

    private suspend fun callPebbleService(request: YahooRequest): InterceptResponse {
        val location = lookupLocation(request.params)
        if (location == null) {
            return InterceptResponse.ERROR
        }
        return when (request) {
            is YahooRequest.LocationRequest -> handleLocationRequest(location)
            is YahooRequest.WeatherRequest -> handleWeatherRequest(request, location)
        }
    }

    private fun handleLocationRequest(
        location: YahooLocation,
    ): InterceptResponse {
        val response = YahooLocationResponse(
            query = YahooLocationResponse.Query(
                results = YahooLocationResponse.Query.Results(
                    place = YahooLocationResponse.Query.Results.Place(
                        woeid = location.woeid,
                        admin1 = YahooLocationResponse.Query.Results.Place.Admin1(
                            content = location.place.administrativeArea
                                ?: location.place.subAdministrativeArea ?: location.place.country
                                ?: "Unknown",
                        ),
                        locality1 = YahooLocationResponse.Query.Results.Place.Locality1(
                            content = location.place.subAdministrativeArea ?: location.place.country
                            ?: "Unknown",
                        ),
                        name = location.place.usefulName() ?: "Unknown",
                    ),
                ),
            ),
        )
        logger.d { "Success from Pebble service (places)" }
        val result = Json.encodeToString(YahooLocationResponse.serializer(), response)
        return InterceptResponse(
            result = result,
            status = 200,
        )
    }

    private suspend fun handleWeatherRequest(
        request: YahooRequest.WeatherRequest,
        location: YahooLocation,
    ): InterceptResponse {
        val units = request.units.asUnits()
        val weather = pebbleWebServices.getWeather(
            latitude = location.coordinates.latitude,
            longitude = location.coordinates.longitude,
            units = units,
            language = Locale.current.toLanguageTag(),
        )
        if (weather == null) {
            logger.d { "No response from Pebble service" }
            return InterceptResponse.ERROR
        }
        val placeName = location.place.usefulName() ?: "Unknown location"
        val country = location.place.isoCountryCode ?: ""
        val temps = weather.conditions.data.observation.tempsFor(units)
        val firstDay = weather.fcstdaily7.data.forecasts.firstOrNull()
        val weatherType = weather.conditions.data.observation.iconCode.toWeatherType()
        val yahooWeatherCode = weatherType.asYahooWeatherCode()
        if (temps == null || firstDay == null) {
            logger.d { "No temps/firstDay from Pebble service" }
            return InterceptResponse.ERROR
        }
        val response = if (request.multiItems) {
            YahooWeatherResponseAlternative(
                query = YahooWeatherResponseAlternative.QueryAlternative(
                    results = YahooWeatherResponseAlternative.QueryAlternative.ResultsAlternative(
                        channel = weather.fcstdaily7.data.forecasts.take(5).map { dayFc ->
                            YahooWeatherResponseAlternative.QueryAlternative.ResultsAlternative.ChannelAlternative(
                                item = YahooWeatherResponseAlternative.QueryAlternative.ResultsAlternative.ChannelAlternative.ItemAlternative(
                                    condition = YahooWeatherResponse.Query.Results.Channel.Item.Condition(
                                        temp = temps.temp,
                                        code = yahooWeatherCode,
                                        text = weather.conditions.data.observation.phrase12Char,
                                        date = firstDay.sunriseRaw.asYahooDate(),
                                    ),
                                    forecast = run {
                                        val code = dayFc.day?.iconCode ?: dayFc.night.iconCode
                                        YahooWeatherResponse.Query.Results.Channel.Item.Forecast(
                                            day = dayFc.dow,
                                            date = dayFc.sunriseRaw.asYahooDate(),
                                            low = dayFc.minTemp,
                                            high = dayFc.maxTemp ?: -1,
                                            text = dayFc.day?.phrase12Char
                                                ?: dayFc.night.phrase12Char,
                                            code = code.toWeatherType().asYahooWeatherCode(),
                                        )
                                    }
                                ),
                                wind = YahooWeatherResponse.Query.Results.Channel.Wind(
                                    chill = 0, // TODO
                                    direction = 0, // TODO
                                    speed = 0, // TODO
                                ),
                                atmosphere = YahooWeatherResponse.Query.Results.Channel.Atmosphere(
                                    humidity = 0, // TODO
                                    visbility = 0, // TODO
                                    pressure = 0, // TODO
                                    rising = 0, // TODO
                                ),
                                location = YahooWeatherResponse.Query.Results.Channel.Location(
                                    city = placeName,
                                    region = location.place.administrativeArea
                                        ?: location.place.subAdministrativeArea
                                        ?: location.place.country
                                        ?: "",
                                    country = country,
                                ),
                                units = YahooWeatherResponse.Query.Results.Channel.Units(
                                    temperature = request.units.first(),
                                    distance = units.asYahooDistanceString(),
                                    pressure = "mb", // TODO
                                    speed = units.asYahooSpeedString(),
                                ),
                                astronomy = YahooWeatherResponse.Query.Results.Channel.Astronomy(
                                    sunrise = firstDay.sunriseRaw.asYahooTime(),
                                    sunset = firstDay.sunsetRaw.asYahooTime(),
                                ),
                            )
                        }
                    ),
                ),
            ).let { Json.encodeToString(YahooWeatherResponseAlternative.serializer(), it) }
        } else {
            YahooWeatherResponse(
                query = YahooWeatherResponse.Query(
                    results = YahooWeatherResponse.Query.Results(
                        channel = YahooWeatherResponse.Query.Results.Channel(
                            item = YahooWeatherResponse.Query.Results.Channel.Item(
                                condition = YahooWeatherResponse.Query.Results.Channel.Item.Condition(
                                    temp = temps.temp,
                                    code = yahooWeatherCode,
                                    text = weather.conditions.data.observation.phrase12Char,
                                    date = firstDay.sunriseRaw.asYahooDate(),
                                ),
                                forecast = weather.fcstdaily7.data.forecasts.take(5).map { dayFc ->
                                    val code = dayFc.day?.iconCode ?: dayFc.night.iconCode
                                    YahooWeatherResponse.Query.Results.Channel.Item.Forecast(
                                        day = dayFc.dow,
                                        date = dayFc.sunriseRaw.asYahooDate(),
                                        low = dayFc.minTemp,
                                        high = dayFc.maxTemp ?: -1,
                                        text = dayFc.day?.phrase12Char ?: dayFc.night.phrase12Char,
                                        code = code.toWeatherType().asYahooWeatherCode(),
                                    )
                                }
                            ),
                            wind = YahooWeatherResponse.Query.Results.Channel.Wind(
                                chill = 0, // TODO
                                direction = 0, // TODO
                                speed = 0, // TODO
                            ),
                            atmosphere = YahooWeatherResponse.Query.Results.Channel.Atmosphere(
                                humidity = 0, // TODO
                                visbility = 0, // TODO
                                pressure = 0, // TODO
                                rising = 0, // TODO
                            ),
                            location = YahooWeatherResponse.Query.Results.Channel.Location(
                                city = placeName,
                                region = location.place.administrativeArea
                                    ?: location.place.subAdministrativeArea
                                    ?: location.place.country
                                    ?: "",
                                country = country,
                            ),
                            units = YahooWeatherResponse.Query.Results.Channel.Units(
                                temperature = request.units.first(),
                                distance = units.asYahooDistanceString(),
                                pressure = "mb", // TODO
                                speed = units.asYahooSpeedString(),
                            ),
                            astronomy = YahooWeatherResponse.Query.Results.Channel.Astronomy(
                                sunrise = firstDay.sunriseRaw.asYahooTime(),
                                sunset = firstDay.sunsetRaw.asYahooTime(),
                            ),
                        )),
                ),
            ).let { Json.encodeToString(YahooWeatherResponse.serializer(), it) }
        }
        logger.d { "Success from Pebble service (weather)" }
        return InterceptResponse(
            result = response,
            status = 200,
        )
    }
}

private val latLonRegex = Regex("""text\s*=\s*"\(([\d-.]+),([\d-.]+)\)""")
private val placeNameRegex = Regex("""text\s*=\s*"(.+?)"""")
private val woeidRegex = Regex("""woeid\s*=\s*(\d+)""")
private val unitsRegex = Regex("u\\s*=\\s*\"([fc])\"")
private val limitRegex = Regex("limit\\s+(\\d+)")

// https://web.archive.org/web/20160206195745/https://developer.yahoo.com/weather/documentation.html
@VisibleForTesting
internal fun String.asYahooWeatherRequest(): YahooRequest? {
    val uri = Uri.parseOrNull(this.lowercase())
    if (uri?.authority == null) {
        return null
    }
    if (uri.authority != "query.yahooapis.com") {
        return null
    }
    if (uri.path != "/v1/public/yql") {
        return null
    }
    val q = uri.getQueryParameter("q")
    if (q == null) {
        return null
    }

    val latLonMatch = latLonRegex.find(q)
    val lat = latLonMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    val lon = latLonMatch?.groupValues?.getOrNull(2)?.toDoubleOrNull()
    val woeid = woeidRegex.find(q)?.groupValues?.getOrNull(1)?.toIntOrNull()
    val placeName = placeNameRegex.find(q)?.groupValues?.getOrNull(1)
    val units = unitsRegex.find(q)?.groupValues?.getOrNull(1)
    val limit = limitRegex.find(q)?.groupValues?.getOrNull(1)?.toIntOrNull()

    val params = when {
        lat != null && lon != null -> YahooWeatherParams.LatLon(
            latitude = lat,
            longitude = lon,
        )

        placeName != null -> YahooWeatherParams.PlaceName(
            placeName = placeName,
        )

        woeid != null -> YahooWeatherParams.Woeid(
            woeid = woeid,
        )

        else -> null
    }
    if (params == null) {
        return null
    }

    val locationQueryIndex = q.indexOf("from geo.places")
    val weatherQueryIndex = q.indexOf("from weather.forecast")
    if (locationQueryIndex == -1 && weatherQueryIndex == -1) {
        return null
    } else if (locationQueryIndex != -1) {
        if (locationQueryIndex < weatherQueryIndex || weatherQueryIndex == -1) {
            return YahooRequest.LocationRequest(params)
        }
    }
    if (weatherQueryIndex != -1 && units != null) {
        return YahooRequest.WeatherRequest(
            params = params,
            units = units,
            multiItems = limit != 1
        )
    }
    return null
}

private fun WeatherUnit.asYahooDistanceString(): String = when (this) {
    WeatherUnit.Metric -> "km"
    WeatherUnit.Imperial -> "mi"
    WeatherUnit.UkHybrid -> "km"
}

private fun WeatherUnit.asYahooSpeedString(): String = when (this) {
    WeatherUnit.Metric -> "kph"
    WeatherUnit.Imperial -> "mph"
    WeatherUnit.UkHybrid -> "kph"
}

// in: 2026-02-06T07:08:00-0800
// out: yyyy/mm/dd
private fun String.asYahooDate(): String {
    return take(10).replace("-", "/")
}

// in: 2026-02-06T17:38:00-0800
// h:mm am/pm (e.g. "4:51 pm")
private val yahooTimeRegex = Regex("T(\\d+):(\\d+):")

private fun String.asYahooTime(): String {
    val match = yahooTimeRegex.find(this)
    val hour = match?.groupValues?.getOrNull(1)?.toInt()
    val minute = match?.groupValues?.getOrNull(2)?.toInt()
    if (hour == null || minute == null) {
        return this
    }
    val hour12 = hour % 12
    val amPm = if (hour < 12) "am" else "pm"
    return "${hour12.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} $amPm"
}

private data class YahooLocation(
    val coordinates: Coordinates,
    val place: Place,
    val woeid: Int,
    val suppliedName: String?,
)

/**
0	tornado
1	tropical storm
2	hurricane
3	severe thunderstorms
4	thunderstorms
5	mixed rain and snow
6	mixed rain and sleet
7	mixed snow and sleet
8	freezing drizzle
9	drizzle
10	freezing rain
11	showers
12	showers
13	snow flurries
14	light snow showers
15	blowing snow
16	snow
17	hail
18	sleet
19	dust
20	foggy
21	haze
22	smoky
23	blustery
24	windy
25	cold
26	cloudy
27	mostly cloudy (night)
28	mostly cloudy (day)
29	partly cloudy (night)
30	partly cloudy (day)
31	clear (night)
32	sunny
33	fair (night)
34	fair (day)
35	mixed rain and hail
36	hot
37	isolated thunderstorms
38	scattered thunderstorms
39	scattered thunderstorms
40	scattered showers
41	heavy snow
42	scattered snow showers
43	heavy snow
44	partly cloudy
45	thundershowers
46	snow showers
47	isolated thundershowers
3200	not available
 */
private fun WeatherType.asYahooWeatherCode(): Int = when (this) {
    WeatherType.PartlyCloudy -> 30
    WeatherType.CloudyDay -> 28
    WeatherType.LightSnow -> 14
    WeatherType.LightRain -> 40
    WeatherType.HeavyRain -> 45
    WeatherType.HeavySnow -> 43
    WeatherType.Generic -> 33
    WeatherType.Sun -> 32
    WeatherType.RainAndSnow -> 5
    WeatherType.Unknown -> 33
}

@Serializable
private data class YahooLocationResponse(
    val query: Query,
) {
    @Serializable
    data class Query(
        val results: Results,
    ) {
        @Serializable
        data class Results(
            val place: Place,
        ) {
            @Serializable
            data class Place(
                val woeid: Int,
                val admin1: Admin1,
                val locality1: Locality1,
                val name: String,
            ) {
                @Serializable
                data class Admin1(
                    val content: String,
                )

                @Serializable
                data class Locality1(
                    val content: String,
                )
            }
        }
    }
}

@Serializable
private data class YahooWeatherResponseAlternative(
    val query: QueryAlternative,
) {
    @Serializable
    data class QueryAlternative(
        val results: ResultsAlternative,
    ) {
        @Serializable
        data class ResultsAlternative(
            val channel: List<ChannelAlternative>,
        ) {
            @Serializable
            data class ChannelAlternative(
                val item: ItemAlternative,
                val wind: YahooWeatherResponse.Query.Results.Channel.Wind,
                val atmosphere: YahooWeatherResponse.Query.Results.Channel.Atmosphere,
                val location: YahooWeatherResponse.Query.Results.Channel.Location,
                val units: YahooWeatherResponse.Query.Results.Channel.Units,
                val astronomy: YahooWeatherResponse.Query.Results.Channel.Astronomy,
            ) {
                @Serializable
                data class ItemAlternative(
                    val condition: YahooWeatherResponse.Query.Results.Channel.Item.Condition,
                    val forecast: YahooWeatherResponse.Query.Results.Channel.Item.Forecast,
                )
            }
        }
    }
}

@Serializable
private data class YahooWeatherResponse(
    val query: Query,
) {
    @Serializable
    data class Query(
        val results: Results,
    ) {
        @Serializable
        data class Results(
            val channel: Channel,
        ) {
            @Serializable
            data class Channel(
                val item: Item,
                val wind: Wind,
                val atmosphere: Atmosphere,
                val location: Location,
                val units: Units,
                val astronomy: Astronomy,
            ) {
                @Serializable
                data class Item(
                    val condition: Condition,
                    val forecast: List<Forecast>,
                ) {
                    @Serializable
                    data class Condition(
                        val temp: Int,
                        val code: Int,
                        val text: String,
                        val date: String,
                    )

                    @Serializable
                    data class Forecast(
                        val day: String,
                        val date: String,
                        val low: Int,
                        val high: Int,
                        val text: String,
                        val code: Int,
                    )
                }

                @Serializable
                data class Wind(
                    val chill: Int,
                    val direction: Int,
                    val speed: Int,
                )

                @Serializable
                data class Atmosphere(
                    val humidity: Int,
                    val visbility: Int,
                    val pressure: Int,
                    val rising: Int,
                )

                @Serializable
                data class Location(
                    val city: String,
                    val region: String,
                    val country: String,
                )

                @Serializable
                data class Units(
                    val temperature: Char,
                    val distance: String,
                    val pressure: String,
                    val speed: String,
                )

                @Serializable
                data class Astronomy(
                    val sunrise: String,
                    val sunset: String,
                )
            }
        }
    }
}

private fun String?.asUnits(): WeatherUnit = when (this) {
    "f" -> WeatherUnit.Imperial
    else -> WeatherUnit.Metric
}

@VisibleForTesting
internal sealed class YahooRequest {
    abstract val params: YahooWeatherParams

    internal data class LocationRequest(
        override val params: YahooWeatherParams,
    ) : YahooRequest()

    internal data class WeatherRequest(
        override val params: YahooWeatherParams,
        val units: String,
        val multiItems: Boolean,
    ) : YahooRequest()
}

@VisibleForTesting
internal sealed class YahooWeatherParams {
    internal data class PlaceName(
        val placeName: String,
    ) : YahooWeatherParams()

    internal data class LatLon(
        val latitude: Double,
        val longitude: Double,
    ) : YahooWeatherParams()

    internal data class Woeid(
        val woeid: Int
    ) : YahooWeatherParams()
}