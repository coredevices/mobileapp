import SwiftUI
import UIKit
import ComposeApp
import Mixpanel
import FirebaseCore
import FirebaseAuth

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        UIImagePickerController.hideCameraSource()
        // Super properties passed at init so the SDK's first automatic events carry them.
        let destinations: Properties = ["analytics_destinations": "mixpanel,collector"]
        let token = "2c9c89f4bc233ba9317f82abe488511a"
        // Mixpanel first: the $ae_updated version marker is shared, only the first instance reports upgrades.
        let mixpanel = Mixpanel.initialize(token: token, trackAutomaticEvents: true, superProperties: destinations)
        // Second SDK instance sending the same events to our collector (BigQuery).
        // iOS keeps opt-out per instance: start the collector opted out if the user opted out of Mixpanel.
        let collector = Mixpanel.initialize(token: token, trackAutomaticEvents: true, instanceName: "collector", optOutTrackingByDefault: mixpanel.hasOptedOutTracking(), superProperties: destinations, serverURL: "https://events.cluster.repebble.com")
        Mixpanel.setMainInstance(name: token) // initialize() makes the last instance main
        let instances = [mixpanel, collector]
        // Kept here rather than read back from the SDK: reset() and opt-out drop super properties.
        var firebaseUid: String? = nil
        let identityLock = NSLock()
        func baseProperties() -> Properties {
            var properties = destinations
            properties["firebase_uid"] = firebaseUid
            return properties
        }
        AlarmKitBridge.register()
        let res = IOSDelegate.shared.didFinishLaunching(application: application, logAnalyticsEvent: { (name: String, params: [String: Any]?) -> Void in
            for instance in instances {
                if let params = params as? Properties {
                    instance.track(event: name, properties: params)
                } else {
                    instance.track(event: name)
                }
            }
        }, addGlobalAnalyticsProperty: { (name: String, value: MixpanelType?) -> Void in
            identityLock.lock()
            defer { identityLock.unlock() }
            if name == "firebase_uid" {
                firebaseUid = value as? String
            }
            for instance in instances {
                if (name == "email") {
                    if let value = value as? String {
                        instance.identify(distinctId: value)
                    } else {
                        instance.reset()
                        instance.registerSuperProperties(baseProperties())
                    }
                } else {
                    if let value = value {
                        instance.registerSuperProperties([name: value])
                    } else {
                        instance.unregisterSuperProperty(name)
                    }
                }
            }
        }, setAnalyticsEnabled: { (enabled: KotlinBoolean) -> Void in
            identityLock.lock()
            defer { identityLock.unlock() }
            for instance in instances {
                if (enabled.boolValue) {
                    instance.optInTracking(properties: baseProperties())
                    instance.registerSuperProperties(baseProperties()) // opt-out cleared it
                } else {
                    instance.optOutTracking()
                }
            }
        })
        SpeechAnalyzerBridge.register()
        UNUserNotificationCenter.current().delegate = self
        // Attaches a QEMU emulator watch for automated testing:
        //   xcrun simctl launch booted coredevices.coreapp -qemu 127.0.0.1:12344
        // Launch arguments can only be set by simctl/Xcode, never by another app or a web link,
        // so this is safe to keep in release builds for CI.
        if let qemuAddress = UserDefaults.standard.string(forKey: "qemu") {
            IOSDelegate.shared.addQemuWatch(address: qemuAddress)
        }
        return res
    }
    
    func application(_ app: UIApplication,
                     open url: URL,
                     options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        if !Auth.auth().canHandle(url) {
            return IOSDelegate.shared.handleOpenUrl(url: url)
        } else {
            return true
        }
    }
    
    func applicationWillTerminate(_ application: UIApplication) {
        IOSDelegate.shared.applicationWillTerminate()
    }
    
    func applicationDidEnterBackground(_ application: UIApplication) {
        IOSDelegate.shared.applicationDidEnterBackground()
    }
    
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        IOSDelegate.shared.applicationDidRegisterForRemoteNotificationsWithDeviceToken(deviceToken: deviceToken)
    }
    
    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable: Any], fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        IOSDelegate.shared.applicationDidReceiveRemoteNotification(userInfo: userInfo, fetchCompletionHandler: { (result: KotlinULong) -> Void in
            completionHandler(UIBackgroundFetchResult(rawValue: result as! UInt) ?? .noData)
        })
    }

    func applicationDidReceiveMemoryWarning(_ application: UIApplication) {
        IOSDelegate.shared.applicationDidReceiveMemoryWarning()
    }
    
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(
            name: nil,
            sessionRole: connectingSceneSession.role)
        if connectingSceneSession.role == .windowApplication {
            configuration.delegateClass = SceneDelegate.self
        }
        return configuration
    }
    
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.alert, .sound, .badge])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        IOSDelegate.shared.userNotificationCenterDidReceiveResponse(response: response, completionHandler: completionHandler)
    }
    
    func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([any UIUserActivityRestoring]?) -> Void) -> Bool {
        return IOSDelegate.shared.applicationWillContinue(userActivity: userActivity)
    }
}

class SceneDelegate: NSObject, UIWindowSceneDelegate, ObservableObject {
    func sceneDidBecomeActive(_ scene: UIScene) {
        IOSDelegate.shared.sceneDidBecomeActive()
    }

    func sceneWillResignActive(_ scene: UIScene) {
        IOSDelegate.shared.sceneWillResignActive()
    }

    func sceneWillEnterForeground(_ scene: UIScene) {
        IOSDelegate.shared.sceneWillEnterForeground()
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
        IOSDelegate.shared.sceneDidEnterBackground()
    }
}

extension UIImagePickerController {
    // WKWebView's file upload menu only offers "Take Photo" when this reports the camera as available.
    static func hideCameraSource() {
        guard let original = class_getClassMethod(self, #selector(isSourceTypeAvailable(_:))),
              let replacement = class_getClassMethod(self, #selector(pebble_isSourceTypeAvailable(_:))) else { return }
        method_exchangeImplementations(original, replacement)
    }

    @objc private class func pebble_isSourceTypeAvailable(_ sourceType: SourceType) -> Bool {
        sourceType != .camera && pebble_isSourceTypeAvailable(sourceType)
    }
}
