import SwiftUI
import UIKit

@MainActor
private var topSafeAreaInset: CGFloat {
    UIApplication.shared.connectedScenes
        .compactMap { $0 as? UIWindowScene }
        .flatMap(\.windows)
        .first { $0.isKeyWindow }?
        .safeAreaInsets.top ?? 0
}

/// Larghezza massima del contenuto. Sui tablet le schermate pensate per il
/// telefono restano centrate a questa larghezza, mentre bande scure, barre e
/// sfondi vanno a tutto schermo. È la stessa larghezza massima delle tendine
/// Material su Android; sui telefoni non interviene mai.
let readableMaxWidth: CGFloat = 640

extension View {
    /// Tiene la vista entro `readableMaxWidth`, centrata nello spazio disponibile.
    func readableWidth(alignment: Alignment = .center) -> some View {
        frame(maxWidth: readableMaxWidth, alignment: alignment)
            .frame(maxWidth: .infinity)
    }
}

/// Signature layout element: full-bleed near-black band under the status bar,
/// light body below. La banda va a tutta larghezza, il contenuto resta entro
/// `readableMaxWidth` — tranne con `fullWidthContent`, per le schermate che
/// usano tutto lo schermo (l'agenda settimanale).
struct DarkHeader<Content: View>: View {
    var roundedBottom: Bool = false
    var contentPadding = EdgeInsets(top: 16, leading: 20, bottom: 16, trailing: 20)
    var fullWidthContent = false
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(contentPadding)
            .frame(maxWidth: fullWidthContent ? .infinity : readableMaxWidth)
            .frame(maxWidth: .infinity)
            // Si arrotonda la forma dello sfondo, non la vista: un clipShape sulla
            // vista ritagliava via la parte di banda che sale sotto la status bar,
            // lasciando una striscia bianca in cima a ogni schermata.
            .background(
                UnevenRoundedRectangle(
                    bottomLeadingRadius: roundedBottom ? 30 : 0,
                    bottomTrailingRadius: roundedBottom ? 30 : 0
                )
                .fill(Color.ink)
                .ignoresSafeArea(edges: .top)
            )
    }
}

struct DarkHeaderTitle: View {
    let title: String
    var subtitle: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(Typo.headlineMedium).foregroundStyle(Color.bone)
            if let subtitle {
                Text(subtitle).font(Typo.bodyMedium).foregroundStyle(Color.onDarkMuted)
            }
        }
    }
}

/// Tendina ancorata al bordo inferiore, a tutta larghezza, alta quanto il suo
/// contenuto. Da iOS 26 le sheet di sistema ad altezza ridotta galleggiano
/// staccate dai bordi; questa si presenta con `anchoredBottomSheet(item:)`.
private struct AnchoredBottomSheetHost<Content: View>: View {
    let onDismissed: () -> Void
    let content: (_ close: @escaping () -> Void) -> Content

    @State private var shown = false
    @State private var dragOffset: CGFloat = 0

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black
                .opacity(shown ? 0.35 : 0)
                .ignoresSafeArea()
                .onTapGesture(perform: close)
            if shown {
                VStack(spacing: 0) {
                    Capsule()
                        .fill(Color.stoneBorder)
                        .frame(width: 36, height: 5)
                        .padding(.top, 8)
                    content(close)
                }
                // Sui tablet la tendina resta larga quanto un telefono, centrata,
                // come la ModalBottomSheet di Android.
                .frame(maxWidth: readableMaxWidth)
                .background(
                    UnevenRoundedRectangle(topLeadingRadius: 26, topTrailingRadius: 26)
                        .fill(Color.bone)
                        .ignoresSafeArea(edges: .bottom)
                )
                .offset(y: max(dragOffset, 0))
                .gesture(
                    DragGesture()
                        .onChanged { dragOffset = $0.translation.height }
                        .onEnded { value in
                            if value.translation.height > 100 {
                                close()
                            } else {
                                withAnimation(.snappy) { dragOffset = 0 }
                            }
                        }
                )
                .transition(.move(edge: .bottom))
            }
        }
        .presentationBackground(.clear)
        .onAppear {
            withAnimation(.snappy) { shown = true }
        }
    }

    private func close() {
        withAnimation(.snappy(duration: 0.25)) {
            shown = false
        } completion: {
            onDismissed()
        }
    }
}

extension View {
    /// Presenta `content` in una tendina ancorata al bordo inferiore (vedi
    /// `AnchoredBottomSheetHost`). `content` riceve la funzione che la chiude
    /// con l'animazione; `item` torna nil a tendina sparita.
    func anchoredBottomSheet<Item: Identifiable, Content: View>(
        item: Binding<Item?>,
        @ViewBuilder content: @escaping (Item, _ close: @escaping () -> Void) -> Content
    ) -> some View {
        // La copertura a schermo intero entra e esce senza la sua animazione
        // di sistema: scorrono solo velo e tendina.
        fullScreenCover(item: Binding(
            get: { item.wrappedValue },
            set: { newValue in
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) { item.wrappedValue = newValue }
            }
        )) { value in
            AnchoredBottomSheetHost(onDismissed: {
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) { item.wrappedValue = nil }
            }) { close in
                content(value, close)
            }
        }
    }

    /// Le tendine dell'app: si fermano sotto il notch — così resta una fascia
    /// per chiudere toccando fuori — e mostrano la maniglia di trascinamento.
    func brandSheet(_ fractions: [CGFloat] = [0.88]) -> some View {
        presentationDetents(Set(fractions.map { PresentationDetent.fraction($0) }))
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(26)
    }

    /// Riporta in `height` l'altezza della vista, a ogni cambio.
    func measureHeight(_ height: Binding<CGFloat>) -> some View {
        onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height.wrappedValue = $0 }
    }

    /// Tendina alta quanto il suo contenuto (`height`, misurato con
    /// `measureHeight`): niente spazio vuoto sopra il bottone in fondo, come la
    /// `ModalBottomSheet` di Android. Se il contenuto supera lo schermo il
    /// sistema ferma la tendina e lo scroll interno fa il resto.
    func fittedBrandSheet(height: CGFloat) -> some View {
        let detent: PresentationDetent = height > 0 ? .height(height.rounded(.up)) : .medium
        return presentationDetents([detent])
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(26)
    }

    /// Campitura scura dietro la status bar. Serve alle schermate il cui
    /// header sta dentro uno ScrollView: lo ScrollView ritaglia sui propri
    /// bordi, quindi la banda dell'header non riesce a salire fin lassù.
    func inkStatusBarBackdrop() -> some View {
        // La banda dell'header non può salire da sé: sta dentro uno ScrollView,
        // che ritaglia sui propri bordi. Si dipinge la fascia di sistema a
        // parte, alta quanto l'inset — che SwiftUI espone solo dentro una
        // GeometryReader, mentre qui serve prima di disegnare.
        overlay(alignment: .top) {
            Color.ink
                .frame(height: topSafeAreaInset)
                .ignoresSafeArea(edges: .top)
        }
    }
}
