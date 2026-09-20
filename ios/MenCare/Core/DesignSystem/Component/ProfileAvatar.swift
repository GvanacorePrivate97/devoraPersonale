import SwiftUI

/// Foto profilo con le iniziali come ripiego: è la stessa in tutte le aree.
/// `editable` aggiunge il segno della macchina fotografica, per dire che
/// toccandolo si cambia la foto; senza, l'avatar può comunque portare altrove.
struct ProfileAvatar: View {
    let initials: String
    let photoPath: String?
    var size: CGFloat = 56
    var corner: CGFloat = 17
    var onTap: (() -> Void)?
    var editable: Bool = false

    var body: some View {
        let avatar = ZStack(alignment: .bottomTrailing) {
            RoundedRectangle(cornerRadius: corner)
                .fill(Color.oliveWood)
                .frame(width: size, height: size)
                .overlay(photoOrInitials)
                .clipShape(RoundedRectangle(cornerRadius: corner))
            if editable {
                RoundedRectangle(cornerRadius: corner * 0.5)
                    .fill(Color.ink)
                    .frame(width: size * 0.36, height: size * 0.36)
                    .overlay(
                        Image(systemName: "camera")
                            .font(.system(size: size * 0.16))
                            .foregroundStyle(Color.bone)
                    )
            }
        }
        if let onTap {
            Button(action: onTap) { avatar }.buttonStyle(.plain)
        } else {
            avatar
        }
    }

    @ViewBuilder
    private var photoOrInitials: some View {
        // La foto ora vive sul server: `photoPath` e' l'URL che manda l'API.
        // Finche' non arriva — o se non arriva — restano le iniziali.
        if let photoPath, let url = URL(string: photoPath), url.scheme != nil {
            AsyncImage(url: url) { phase in
                if let image = phase.image {
                    image
                        .resizable()
                        .scaledToFill()
                        .frame(width: size, height: size)
                } else {
                    initialsLabel
                }
            }
        } else if let photoPath, let image = UIImage(contentsOfFile: photoPath) {
            // Un percorso locale resta valido: e' quello che usano le anteprime.
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(width: size, height: size)
        } else {
            initialsLabel
        }
    }

    private var initialsLabel: some View {
        Text(initials.uppercased())
            .font(Typo.cormorant(size * 0.34, weight: .regular))
            .foregroundStyle(Color.bone)
    }
}
