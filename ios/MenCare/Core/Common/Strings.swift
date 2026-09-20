import Foundation

/// Shorthand for the Italian string resources ported from the Android app —
/// the keys are the same `strings.xml` names, so the two stay diffable.
func L(_ key: String) -> String {
    NSLocalizedString(key, comment: "")
}

func L(_ key: String, _ args: CVarArg...) -> String {
    String(format: NSLocalizedString(key, comment: ""), arguments: args)
}
