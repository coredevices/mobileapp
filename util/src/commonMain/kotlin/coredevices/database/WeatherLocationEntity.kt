package coredevices.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

private val logger = Logger.withTag("WeatherLocationEntity")

@Entity
@Serializable
data class WeatherLocationEntity(
    @PrimaryKey val key: Uuid,
    val orderIndex: Int,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
    val currentLocation: Boolean,
)

@Dao
interface WeatherLocationDao {
    @Upsert
    suspend fun upsert(location: WeatherLocationEntity)

    @Delete
    suspend fun delete(location: WeatherLocationEntity)

    @Query("UPDATE WeatherLocationEntity SET orderIndex = :newIndex WHERE `key` = :key")
    suspend fun updateOrder(key: Uuid, newIndex: Int)

    @Query("SELECT * FROM WeatherLocationEntity ORDER BY orderIndex ASC")
    fun getAllLocationsFlow(): Flow<List<WeatherLocationEntity>>

    @Query("SELECT * FROM WeatherLocationEntity ORDER BY orderIndex ASC")
    suspend fun getAllLocations(): List<WeatherLocationEntity>
}

private const val HAVE_INSERTED_DEFAULT_WEATHER_LOCATION_KEY = "have_inserted_default_weather_location"

fun WeatherLocationDao.insertDefaultWeatherLocationOnce(settings: Settings) {
    GlobalScope.launch {
        if (settings.getBoolean(HAVE_INSERTED_DEFAULT_WEATHER_LOCATION_KEY, false)) {
            return@launch
        }
        settings.putBoolean(HAVE_INSERTED_DEFAULT_WEATHER_LOCATION_KEY, true)
        if (getAllLocations().isNotEmpty()) {
            return@launch
        }
        logger.d { "Inserting default weather location" }
        upsert(
            WeatherLocationEntity(
                key = Uuid.random(),
                name = "Current Location",
                latitude = null,
                longitude = null,
                currentLocation = true,
                orderIndex = 0
            )
        )
    }
}