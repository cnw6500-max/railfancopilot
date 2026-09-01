import StoreKit
import Foundation

@MainActor
class StoreManager: ObservableObject {

    static let shared = StoreManager()

    // Product ID — must match what you create in App Store Connect
    static let proProductID = "com.ronminor.railfancopilot.pro"

    @Published var proProduct: Product? = nil
    @Published var isPurchasing = false
    @Published var purchaseError: String? = nil

    private var transactionListener: Task<Void, Error>?

    init() {
        transactionListener = listenForTransactions()
        Task { await loadProducts() }
    }

    deinit {
        transactionListener?.cancel()
    }

    // ── Load products from App Store ──────────────────────────────────────────
    func loadProducts() async {
        do {
            let products = try await Product.products(for: [StoreManager.proProductID])
            proProduct = products.first
        } catch {
            print("Failed to load products: \(error)")
        }
    }

    // ── Purchase ──────────────────────────────────────────────────────────────
    func purchase() async -> Bool {
        guard let product = proProduct else {
            purchaseError = "Product not available. Check your internet connection."
            return false
        }

        isPurchasing = true
        purchaseError = nil

        do {
            let result = try await product.purchase()
            isPurchasing = false

            switch result {
            case .success(let verification):
                let transaction = try checkVerified(verification)
                await transaction.finish()
                return true
            case .userCancelled:
                return false
            case .pending:
                purchaseError = "Purchase is pending approval."
                return false
            @unknown default:
                return false
            }
        } catch {
            isPurchasing = false
            purchaseError = "Purchase failed: \(error.localizedDescription)"
            return false
        }
    }

    // ── Restore purchases ─────────────────────────────────────────────────────
    func restorePurchases() async -> Bool {
        do {
            try await AppStore.sync()
            return await checkEntitlement()
        } catch {
            purchaseError = "Restore failed: \(error.localizedDescription)"
            return false
        }
    }

    // ── Check if Pro is already purchased ────────────────────────────────────
    func checkEntitlement() async -> Bool {
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result,
               transaction.productID == StoreManager.proProductID {
                return true
            }
        }
        return false
    }

    // ── Listen for transactions (handles deferred purchases, renewals) ──────────
    private func listenForTransactions() -> Task<Void, Error> {
        Task.detached {
            for await result in Transaction.updates {
                if case .verified(let transaction) = result {
                    await transaction.finish()
                    // Post notification so UpgradeView can recheck and unlock premium
                    await MainActor.run {
                        NotificationCenter.default.post(
                            name: .storeTransactionUpdated, object: nil)
                    }
                }
            }
        }
    }

    // ── Verify transaction ────────────────────────────────────────────────────
    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified:
            throw StoreError.failedVerification
        case .verified(let value):
            return value
        }
    }

    // ── Formatted price ───────────────────────────────────────────────────────
    var formattedPrice: String {
        proProduct?.displayPrice ?? "$3.99"
    }
}

enum StoreError: Error {
    case failedVerification
}

extension Notification.Name {
    static let storeTransactionUpdated = Notification.Name("storeTransactionUpdated")
}
