import SwiftUI
import UIKit
import ComposeApp
import Mixpanel

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        let mixpanel = Mixpanel.initialize(token: "2c9c89f4bc233ba9317f82abe488511a", trackAutomaticEvents: true)
        return IOSDelegate.shared.didFinishLaunching(application: application, logAnalyticsEvent: { (name: String, params: [String: Any]?) -> Void in
            if let params = params as? Properties {
                let properties = params
                mixpanel.track(event: name, properties: properties)
            } else {
                mixpanel.track(event: name)
            }
        }, addGlobalAnalyticsProperty: { (name: String, value: MixpanelType?) -> Void in
            if (name == "email") {
                if let value = value as? String {
                    mixpanel.identify(distinctId: value)
                } else {
                    mixpanel.reset()
                }
            } else {
                if let value = value {
                    mixpanel.registerSuperPropertiesOnce([name: value])
                } else {
                    mixpanel.unregisterSuperProperty(name)
                }
            }
        }, setAnalyticsEnabled: { (enabled: KotlinBoolean) -> Void in
            if (enabled.boolValue) {
                mixpanel.optInTracking()
            } else {
                mixpanel.optOutTracking()
            }
        })
    }
    
    func application(_ app: UIApplication,
                     open url: URL,
                     options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        return IOSDelegate.shared.handleOpenUrl(url: url)
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
