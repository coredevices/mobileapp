//
//  IOSLocation.swift
//  libpebble-swift
//
//  Created by crc32 on 22/07/2025.
//

import Foundation
import CoreLocation

@objc(IOSLocation) public class IOSLocation: NSObject, CLLocationManagerDelegate {
    
    private let locationCallback: (CLLocation?) -> Void
    private let authorizationCallback: (Bool) -> Void
    private let errorCallback: (Error) -> Void
    private let locationManager = CLLocationManager()
    
    @objc public init(
        locationCallback: @escaping (CLLocation?) -> Void,
        authorizationCallback: @escaping (Bool) -> Void,
        errorCallback: @escaping (Error) -> Void
    ) {
        self.locationCallback = locationCallback
        self.authorizationCallback = authorizationCallback
        self.errorCallback = errorCallback
        super.init()
        self.locationManager.delegate = self
    }
    
    @objc public func start() {
        locationManager.requestWhenInUseAuthorization()
    }

    @objc public func stop() {
        locationManager.stopUpdatingLocation()
    }

    @objc public func lastLocation() -> CLLocation? {
        return locationManager.location
    }

    @objc public func setHighAccuracy(_ highAccuracy: Bool) {
        locationManager.desiredAccuracy = highAccuracy
            ? kCLLocationAccuracyBestForNavigation
            : kCLLocationAccuracyHundredMeters
    }
    
    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        locationCallback(locations.last)
    }
    
    public func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        switch status {
        case .authorizedAlways, .authorizedWhenInUse:
            locationManager.startUpdatingLocation()
            authorizationCallback(true)
        default:
            authorizationCallback(false)
        }
    }
    
    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        self.errorCallback(error)
    }
}
