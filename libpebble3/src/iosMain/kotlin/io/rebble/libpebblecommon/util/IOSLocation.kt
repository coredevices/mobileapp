package io.rebble.libpebblecommon.util

import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBestForNavigation
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.darwin.NSObject

class IOSLocation(
    private val locationCallback: (CLLocation?) -> Unit,
    private val authorizationCallback: (Boolean) -> Unit,
    private val errorCallback: (NSError?) -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {
    private val locationManager = CLLocationManager()

    init {
        locationManager.delegate = this
    }

    fun start() {
        locationManager.requestWhenInUseAuthorization()
    }

    fun stop() {
        locationManager.stopUpdatingLocation()
    }

    fun lastLocation(): CLLocation? = locationManager.location

    fun setHighAccuracy(highAccuracy: Boolean) {
        locationManager.setDesiredAccuracy(
            if (highAccuracy) kCLLocationAccuracyBestForNavigation
            else kCLLocationAccuracyHundredMeters
        )
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        locationCallback(didUpdateLocations.lastOrNull() as? CLLocation)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedAlways,
            kCLAuthorizationStatusAuthorizedWhenInUse -> {
                manager.startUpdatingLocation()
                authorizationCallback(true)
            }
            else -> authorizationCallback(false)
        }
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        errorCallback(didFailWithError)
    }
}
