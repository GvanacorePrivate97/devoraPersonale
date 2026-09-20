import SwiftUI

/// "Giornata al completo": a calendar page with every day crossed out and a
/// stopwatch whose hand keeps turning, sweeping an olive wedge behind it — the
/// wait for a slot to free up. Decorative only.
struct FullyBookedIllustration: View {
    /// Drawing grid of the illustration: every coordinate below is in these units.
    /// The artboard is cropped to the drawing (rings on top, shadow at the bottom)
    /// so the picture sits centered in its frame.
    private static let artSize = CGSize(width: 240, height: 138)
    private static let artTop: CGFloat = 22
    /// One full turn of the minute hand.
    private static let turnSeconds: Double = 6

    var body: some View {
        TimelineView(.animation) { timeline in
            let elapsed = timeline.date.timeIntervalSinceReferenceDate
            let angle = elapsed.truncatingRemainder(dividingBy: Self.turnSeconds) / Self.turnSeconds * 360
            Canvas { context, size in
                let u = size.width / Self.artSize.width
                context.translateBy(x: 0, y: -Self.artTop * u)
                context.fill(
                    Path(ellipseIn: CGRect(x: 28 * u, y: 146 * u, width: 190 * u, height: 12 * u)),
                    with: .color(.stone)
                )
                drawCalendar(context, u: u)
                drawStopwatch(context, u: u, angle: angle)
            }
        }
        .aspectRatio(Self.artSize.width / Self.artSize.height, contentMode: .fit)
        .accessibilityHidden(true)
    }

    private func drawCalendar(_ context: GraphicsContext, u: CGFloat) {
        let page = CGRect(x: 18 * u, y: 34 * u, width: 130 * u, height: 116 * u)
        let corner = 10 * u

        context.fill(Path(roundedRect: page, cornerRadius: corner), with: .color(.bone))
        // Olive header band, rounded on top only.
        context.fill(
            Path(roundedRect: CGRect(x: page.minX, y: page.minY, width: page.width, height: 30 * u), cornerRadius: corner),
            with: .color(.oliveWood)
        )
        context.fill(
            Path(CGRect(x: page.minX, y: page.minY + 20 * u, width: page.width, height: 10 * u)),
            with: .color(.oliveWood)
        )
        context.stroke(Path(roundedRect: page, cornerRadius: corner), with: .color(.ink), lineWidth: 2.5 * u)
        for x in [46.0, 112.0] {
            context.fill(
                Path(roundedRect: CGRect(x: x * u, y: 26 * u, width: 8 * u, height: 16 * u), cornerRadius: 4 * u),
                with: .color(.ink)
            )
        }

        // Every day taken: a cross on each cell.
        let cell = CGSize(width: 22 * u, height: 16 * u)
        let gap = 8 * u
        let gridLeft = page.minX + (page.width - (4 * cell.width + 3 * gap)) / 2
        let gridTop = page.minY + 40 * u
        let cross = 4 * u
        for row in 0..<3 {
            for col in 0..<4 {
                let origin = CGPoint(
                    x: gridLeft + CGFloat(col) * (cell.width + gap),
                    y: gridTop + CGFloat(row) * (cell.height + gap)
                )
                context.fill(
                    Path(roundedRect: CGRect(origin: origin, size: cell), cornerRadius: 4 * u),
                    with: .color(.stone)
                )
                let center = CGPoint(x: origin.x + cell.width / 2, y: origin.y + cell.height / 2)
                var mark = Path()
                mark.move(to: CGPoint(x: center.x - cross, y: center.y - cross))
                mark.addLine(to: CGPoint(x: center.x + cross, y: center.y + cross))
                mark.move(to: CGPoint(x: center.x - cross, y: center.y + cross))
                mark.addLine(to: CGPoint(x: center.x + cross, y: center.y - cross))
                context.stroke(
                    mark,
                    with: .color(Color.ink.opacity(0.55)),
                    style: StrokeStyle(lineWidth: 1.8 * u, lineCap: .round)
                )
            }
        }
    }

    private func drawStopwatch(_ context: GraphicsContext, u: CGFloat, angle: Double) {
        let center = CGPoint(x: 182 * u, y: 98 * u)
        let radius = 40 * u

        // Crown and side button sit behind the face.
        context.fill(
            Path(roundedRect: CGRect(x: 177 * u, y: 50 * u, width: 10 * u, height: 10 * u), cornerRadius: 2 * u),
            with: .color(.ink)
        )
        context.fill(
            Path(roundedRect: CGRect(x: 171 * u, y: 44 * u, width: 22 * u, height: 7 * u), cornerRadius: 3.5 * u),
            with: .color(.ink)
        )
        context.fill(circle(at: offset(center, polar(radius + 2 * u, 45)), radius: 5.5 * u), with: .color(.ink))

        context.fill(circle(at: center, radius: radius), with: .color(.bone))
        var wedge = Path()
        wedge.move(to: center)
        wedge.addArc(
            center: center, radius: radius * 0.84,
            startAngle: .degrees(-90), endAngle: .degrees(-90 + angle), clockwise: false
        )
        wedge.closeSubpath()
        context.fill(wedge, with: .color(Color.oliveWood.opacity(0.22)))
        context.stroke(circle(at: center, radius: radius), with: .color(.ink), lineWidth: 3 * u)

        for i in 0..<12 {
            let length = i % 3 == 0 ? 7 * u : 4 * u
            let outer = radius - 6 * u
            context.stroke(
                line(from: offset(center, polar(outer - length, Double(i) * 30)), to: offset(center, polar(outer, Double(i) * 30))),
                with: .color(.ink),
                style: StrokeStyle(lineWidth: 1.6 * u, lineCap: .round)
            )
        }

        context.stroke(
            line(from: center, to: offset(center, polar(radius * 0.42, angle / 12))),
            with: .color(.ink),
            style: StrokeStyle(lineWidth: 3.5 * u, lineCap: .round)
        )
        context.stroke(
            line(from: center, to: offset(center, polar(radius * 0.7, angle))),
            with: .color(.oliveWood),
            style: StrokeStyle(lineWidth: 3 * u, lineCap: .round)
        )
        context.fill(circle(at: center, radius: 4 * u), with: .color(.ink))
        context.fill(circle(at: center, radius: 1.8 * u), with: .color(.oliveWood))
    }

    private func circle(at center: CGPoint, radius: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
    }

    private func line(from start: CGPoint, to end: CGPoint) -> Path {
        var path = Path()
        path.move(to: start)
        path.addLine(to: end)
        return path
    }

    private func offset(_ point: CGPoint, _ delta: CGVector) -> CGPoint {
        CGPoint(x: point.x + delta.dx, y: point.y + delta.dy)
    }

    /// Vector of length `distance`, `degrees` clockwise from twelve o'clock.
    private func polar(_ distance: CGFloat, _ degrees: Double) -> CGVector {
        let radians = (degrees - 90) * .pi / 180
        return CGVector(dx: distance * cos(radians), dy: distance * sin(radians))
    }
}
