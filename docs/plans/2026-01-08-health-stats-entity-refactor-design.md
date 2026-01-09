# Health Stats Entity Refactor Design

**Date:** 2026-01-08
**Status:** Approved

## Overview

Refactor HealthStatsSync.kt to use the `@GenerateRoomEntity` pattern instead of manually constructing and sending BlobDB messages. This aligns health stats syncing with the existing WatchSettings pattern, where Room entities automatically sync to the watch via BlobDB infrastructure.

## Current State

HealthStatsSync.kt currently:
- Manually constructs binary payloads for 16 different health statistics
- Directly calls `BlobDBService.send()` to push stats to watch
- Sends stats at specific trigger points (after data received, periodic updates, manual requests)
- Handles: 7 daily movement records, 7 daily sleep records, 2 averages

## Design Goals

1. **Declarative syncing**: Store computed stats in Room, let BlobDB infrastructure handle watch syncing
2. **Cleaner separation**: Computation happens once in HealthService, syncing is automatic
3. **Follows existing patterns**: Use same `@GenerateRoomEntity` pattern as WatchSettings
4. **Preserve behavior**: Exclude days without data from averages (existing logic)

## Entity Structure

### HealthStat Entity

```kotlin
@GenerateRoomEntity(
    primaryKey = "key",
    databaseId = BlobDatabase.HealthStats,
    windowBeforeSecs = -1,  // No time-based filtering
    windowAfterSecs = -1,
    onlyInsertAfter = false,
    sendDeletions = true,  // Remove stats from watch when deleted
)
data class HealthStat(
    val key: String,  // "monday_movementData", "average_dailySteps", etc.
    val payload: ByteArray,  // Pre-computed binary payload
    val lastUpdated: Long = System.currentTimeMillis(),  // For debugging
) : BlobDbItem {
    override fun key(): UByteArray =
        key.encodeToByteArray().toUByteArray()

    override fun value(platform: WatchType, capabilities: Set<ProtocolCapsFlag>): UByteArray =
        payload.toUByteArray()

    override fun recordHashCode(): Int =
        key.hashCode() + payload.contentHashCode()
}
```

**Key design decisions:**
- Single entity type handles all 16 stats (simple key-value store)
- Payload is pre-computed binary data (computation stays in domain logic)
- `lastUpdated` timestamp for debugging staleness issues
- `@GenerateRoomEntity` generates DAO and syncing infrastructure automatically

## Data Flow

### Old Flow
```
HealthService → sendHealthStatsToWatch() → Direct BlobDB.send()
```

### New Flow
```
HealthService → updateHealthStatsInDatabase() → HealthStatDao.insertOrReplace() → BlobDB auto-syncs
```

## Implementation Details

### New Function: updateHealthStatsInDatabase()

Located in HealthStatsSync.kt, replaces all old `send*` functions:

```kotlin
suspend fun updateHealthStatsInDatabase(
    healthDao: HealthDao,
    healthStatDao: HealthStatDao,
    today: LocalDate,
    startDate: LocalDate,
    timeZone: TimeZone
) {
    val stats = mutableListOf<HealthStat>()

    // Compute averages (existing calculateHealthAverages logic)
    val averages = calculateHealthAverages(healthDao, startDate, today, timeZone)
    stats.add(HealthStat(
        key = "average_dailySteps",
        payload = encodeUInt(averages.averageStepsPerDay.toUInt()).toByteArray()
    ))
    stats.add(HealthStat(
        key = "average_sleepDuration",
        payload = encodeUInt(averages.averageSleepSecondsPerDay.toUInt()).toByteArray()
    ))

    // Compute weekly movement data
    val oldestDate = today.minus(DatePeriod(days = 6))
    val rangeStart = oldestDate.startOfDayEpochSeconds(timeZone)
    val rangeEnd = today.plus(DatePeriod(days = 1)).startOfDayEpochSeconds(timeZone)
    val allAggregates = healthDao.getDailyMovementAggregates(rangeStart, rangeEnd)

    repeat(7) { offset ->
        val day = today.minus(DatePeriod(days = offset))
        val dayStart = day.startOfDayEpochSeconds(timeZone)
        val aggregate = allAggregates.find {
            LocalDate.parse(it.day).atStartOfDayIn(timeZone).epochSeconds == dayStart
        }

        // Add movement stat
        stats.add(HealthStat(
            key = MOVEMENT_KEYS[day.dayOfWeek]!!,
            payload = movementPayload(dayStart, aggregate?.toHealthAggregates()).toByteArray()
        ))

        // Add sleep stat
        val mainSleep = fetchAndGroupDailySleep(healthDao, dayStart, timeZone)
        stats.add(HealthStat(
            key = SLEEP_KEYS[day.dayOfWeek]!!,
            payload = sleepPayload(dayStart, mainSleep).toByteArray()
        ))
    }

    // Batch insert all 16 stats
    healthStatDao.insertOrReplace(stats)
}
```

### Trigger Points in HealthService

Replace direct BlobDB sends with database updates:

1. **After receiving health data** (in `handleSessionClose`)
   - Triggers `_healthUpdateFlow.emit()` (existing)
   - Calls `updateHealthStatsInDatabase()` (new)

2. **Periodic updates** (in `startPeriodicStatsUpdate`)
   - Updates database every 24 hours instead of sending

3. **Manual requests** (in `sendHealthAveragesToWatch`)
   - Updates database (BlobDB auto-syncs to watch)

**Example refactor:**
```kotlin
private suspend fun updateHealthStats() {
    // ... existing latestTimestamp check ...

    // OLD: sendHealthStatsToWatch(healthDao, blobDBService, today, startDate, timeZone)
    // NEW:
    updateHealthStatsInDatabase(
        healthDao = healthDao,
        healthStatDao = healthStatDao,  // Inject this dependency
        today = today,
        startDate = startDate,
        timeZone = timeZone
    )
}
```

### Average Calculation Behavior

The existing `calculateHealthAverages()` function excludes days without data from averages:

```kotlin
val averageStepsPerDay = if (stepDaysWithData > 0) {
    (totalSteps / stepDaysWithData).toInt()  // Divides by days WITH data
} else {
    0
}
```

**This behavior is preserved** - we call the same function, just store results instead of sending directly.

## Migration Plan

### Files to Create
- `libpebble3/src/commonMain/kotlin/io/rebble/libpebblecommon/database/entity/HealthStat.kt`

### Files to Modify
1. **HealthStatsSync.kt**: Replace `send*` functions with `updateHealthStatsInDatabase()`
2. **HealthService.kt**: Inject `HealthStatDao`, call new update function instead of old send functions
3. **BlobDbDaos.kt**: Add `HealthStatDao` to the set of managed DAOs

### Code to Delete from HealthStatsSync.kt

**Delete these functions:**
- `sendHealthStatsToWatch()`
- `sendWeeklyMovementData()`
- `sendWeeklySleepData()`
- `sendTodayMovementData()`
- `sendRecentSleepData()`
- `sendSingleDaySleep()`
- `sendAverageMonthlySteps()`
- `sendAverageMonthlySleep()`
- `sendHealthStat()` (direct BlobDB send)

**Delete parameter:** No longer need `BlobDBService` in function signatures

### Code to Keep in HealthStatsSync.kt

**Payload generation functions:**
- `movementPayload()`
- `sleepPayload()`
- `encodeUInt()`

**Helper functions:**
- `LocalDate.startOfDayEpochSeconds()`
- Conversion extensions: `kilocalories()`, `kilometers()`, `toSeconds()`, `safeUInt()`

**Constants:**
- `MOVEMENT_KEYS` map
- `SLEEP_KEYS` map
- Payload size constants
- `HEALTH_STATS_VERSION`

## Benefits

1. **Simpler**: Computation and syncing are separate concerns
2. **Reliable**: BlobDB infrastructure handles retries, connection state
3. **Consistent**: Same pattern as WatchSettings and other blob entities
4. **Debuggable**: Stats stored in Room database, can inspect/debug
5. **Less code**: Delete ~200 lines of manual BlobDB sending logic

## Trade-offs

- **Storage**: 16 additional Room database rows (minimal overhead)
- **Indirection**: Stats go through database instead of direct send (negligible latency)
- **Binary payloads**: Still stored as opaque bytes (could decode in future if needed)

## Testing Considerations

- Verify all 16 stats sync to watch after health data received
- Confirm averages still exclude days without data
- Test periodic 24-hour updates still trigger
- Verify manual sync requests work
- Check stats persist across app restarts
