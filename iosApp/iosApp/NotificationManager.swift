import Foundation
import UserNotifications
import CoreLocation

@MainActor
class NotificationManager: NSObject, ObservableObject, UNUserNotificationCenterDelegate {

    static let shared = NotificationManager()
    @Published var isAuthorized = false

    override init() {
        super.init()
        UNUserNotificationCenter.current().delegate = self
        checkAuthorization()
    }

    func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            Task { @MainActor in
                self.isAuthorized = granted
            }
        }
    }

    private func checkAuthorization() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            Task { @MainActor in
                self.isAuthorized = settings.authorizationStatus == .authorized
            }
        }
    }

    func sendApproachNotification(trainSymbol: String, railroad: String, etaMinutes: Int, locationName: String) {
        guard isAuthorized else { return }

        let content = UNMutableNotificationContent()
        content.title = "🚂 Train Approaching \(locationName)"
        content.body = "\(railroad) \(trainSymbol) is approximately \(etaMinutes) min away"
        content.sound = .default
        content.categoryIdentifier = "TRAIN_APPROACH"

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
        let id = "approach-\(trainSymbol)-\(Date().timeIntervalSince1970)"
        let request = UNNotificationRequest(identifier: id, content: content, trigger: trigger)

        UNUserNotificationCenter.current().add(request)
    }

    // Show notification even when app is in foreground
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }
}
