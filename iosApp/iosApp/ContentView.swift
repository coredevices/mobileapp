import UIKit
import Foundation
import SwiftUI
import Combine
import ComposeApp
import PebbleProtocol

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
                .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
                .ignoresSafeArea(edges: .all)
                .background(Color(
                    .sRGB,
                    red: 65,
                    green: 65,
                    blue: 65,
                    opacity: 1.0
                ))
                .onReceive(NotificationCenter.default.publisher(for:
                    Notification.Name(NotificationBridgeConfiguration.notificationName("notification-dismissal-setting-changed")))
                    .receive(on: RunLoop.main)) { notification in
                    guard let enabled = notification.userInfo?["enabled"] as? Bool else { return }
                    NotificationDismissalSettings.onBothDevices = enabled
                }
                .onReceive(NotificationCenter.default.publisher(for:
                    Notification.Name(NotificationBridgeConfiguration.notificationName("selected-watch-secure-notifications")))
                    .receive(on: RunLoop.main)) { notification in
                    let identifier = notification.userInfo?["selectedWatchIdentifier"] as? String
                    Task { @MainActor in
                        PairedSelectedWatchMenu.receive(identifier: identifier,
                            addWatch: notification.userInfo?["intent"] as? String == "add-watch")
                    }
                }
    }
}


