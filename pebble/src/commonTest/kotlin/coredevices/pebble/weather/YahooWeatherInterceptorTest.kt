package coredevices.pebble.weather

import kotlin.test.Test
import kotlin.test.assertEquals

class YahooWeatherInterceptorTest {
    @Test
    fun parseLocationFromNameYql() {
        // select woeid, name from geo.places(1) where text="mountain view"
        val input = """http://query.yahooapis.com/v1/public/yql?q=select%20woeid,%20name%20from%20geo.places(1)%20where%20text=%22mountain%20view%22&format=json""""
        val expected = YahooRequest.LocationRequest(
            params = YahooWeatherParams.PlaceName(
                placeName = "mountain view",
            )
        )
        assertEquals(expected, input.asYahooWeatherRequest())
    }

    @Test
    fun parseLocationFromLatLonYql() {
        // select woeid, admin1, locality1, name  from geo.places where text = "(34.12345,-123.12345)"
        val input = """http://query.yahooapis.com/v1/public/yql?q=select%20woeid,%20admin1,%20locality1,%20name%20%20from%20geo.places%20where%20text%20=%20%22(34.12345,-123.12345)%22&format=json""""
        val expected = YahooRequest.LocationRequest(
            params = YahooWeatherParams.LatLon(
                latitude = 34.12345,
                longitude = -123.12345,
            )
        )
        assertEquals(expected, input.asYahooWeatherRequest())
    }

    @Test
    fun parseWeatherFromLocationNameYql() {
        // select * from weather.forecast where woeid in (select woeid from geo.places(1) where text="mission district, san francisco, california, 90110, united states") and u="f" limit 1
        val input = """https://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20weather.forecast%20where%20woeid%20in%20(select%20woeid%20from%20geo.places(1)%20where%20text%3D%22Mission%20District%2C%20San%20Francisco%2C%20California%2C%2090110%2C%20United%20States%22)%20and%20u%3D%22f%22%20limit%201&format=json&env=store://datatables.org/alltableswithkeys""""
        val expected = YahooRequest.WeatherRequest(
            params = YahooWeatherParams.PlaceName(
                placeName = "mission district, san francisco, california, 90110, united states",
            ),
            units = "f",
            multiItems = false,
        )
        assertEquals(expected, input.asYahooWeatherRequest())
    }

    @Test
    fun parseWeatherFromLocationWoiedYql() {
        // select  item.condition, item.forecast, astronomy, wind, atmosphere from weather.forecast where woeid = 123456 and u = "c"
        val input = """https://query.yahooapis.com/v1/public/yql?q=select%20%20item.condition%2C%20item.forecast%2C%20astronomy%2C%20wind%2C%20atmosphere%20from%20weather.forecast%20where%20woeid%20%3D%20123456%20and%20u%20%3D%20%22c%22&format=json&env=store://datatables.org/alltableswithkeys""""
        val expected = YahooRequest.WeatherRequest(
            params = YahooWeatherParams.Woeid(
                woeid = 123456,
            ),
            units = "c",
            multiItems = true,
        )
        assertEquals(expected, input.asYahooWeatherRequest())
    }

    @Test
    fun parseWeatherFromLocationLatLonYql() {
        // select * from weather.forecast where woeid in (select woeid from geo.places(1) where text = "(34.12345,-123.12345)") and u="f" limit 1
        val input = """https://query.yahooapis.com/v1/public/yql?q=select%20%2A%20from%20weather.forecast%20where%20woeid%20in%20%28select%20woeid%20from%20geo.places%281%29%20where%20text%20%3D%20%22%2834.12345%2C-123.12345%29%22%29%20and%20u%3D%22f%22%20limit%201&format=json&env=store://datatables.org/alltableswithkeys""""
        val expected = YahooRequest.WeatherRequest(
            params = YahooWeatherParams.LatLon(
                latitude = 34.12345,
                longitude = -123.12345,
            ),
            units = "f",
            multiItems = false,
        )
        assertEquals(expected, input.asYahooWeatherRequest())
    }
}