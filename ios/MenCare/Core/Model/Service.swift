import Foundation

struct Service: Identifiable, Hashable {
    let id: String
    var name: String
    var durationMinutes: Int
    var priceCents: Int64
    var description: String?
    var featured: Bool = false
}
