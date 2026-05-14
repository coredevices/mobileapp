//
//  IOSLocation.h
//  LibPebbleSwift
//
//  Created by crc32 on 22/07/2025.
//

#include <Foundation/Foundation.h>
#include <CoreLocation/CoreLocation.h>

@interface IOSLocation : NSObject
- (void) start;
- (void) stop;
- (CLLocation * _Nullable) lastLocation;
- (void) setHighAccuracy:(BOOL)highAccuracy;
- (IOSLocation* _Nonnull)initWithLocationCallback:(void (^ _Nonnull)(CLLocation * _Nullable))locationCallback
                      authorizationCallback:(void (^ _Nonnull)(BOOL))authorizationCallback
                          errorCallback:(void (^ _Nonnull)(NSError * _Nonnull))errorCallback;
@end
