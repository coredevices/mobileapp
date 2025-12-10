import SwiftUI
import FirebaseCore
import ComposeApp

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    init() {
        FirebaseApp.configure()
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL(perform: handleURL)
        }
    }
    
    func handleURL(_ url: URL) {
        IOSDelegate.shared.handleOpenUrl(url: url)
    }
}
