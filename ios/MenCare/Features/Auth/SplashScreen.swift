import SwiftUI

/// Mostrato mentre si controlla se la sessione salvata vale ancora: chi decide
/// quando sparire e' `RootView`, non lo splash.
struct SplashScreen: View {
    @State private var progress: CGFloat = 0

    var body: some View {
        ZStack {
            Color.ink.ignoresSafeArea()
            VStack(spacing: 0) {
                Image("logo-mark")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 120, height: 120)
                Text(L("auth_brand_name"))
                    .font(Typo.displayMedium)
                    .foregroundStyle(Color.bone)
                    .padding(.top, 20)
                Text(L("auth_brand_claim").uppercased())
                    .font(Typo.labelMedium)
                    .kerning(2)
                    .foregroundStyle(Color.oliveWood)
                    .padding(.top, 4)
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.bone.opacity(0.12))
                        Capsule().fill(Color.oliveWood).frame(width: geo.size.width * progress)
                    }
                }
                .frame(width: 150, height: 4)
                .padding(.top, 40)
            }
            VStack {
                Spacer()
                Text(L("auth_powered_by"))
                    .font(Typo.bodySmall)
                    .foregroundStyle(Color.onDarkMuted)
                    .padding(.bottom, 40)
            }
        }
        .task {
            withAnimation(.easeInOut(duration: 1.4)) { progress = 1 }
        }
    }
}
