import SwiftUI

/// Day timetable shared by the owner's and the staff agenda: same hours, same
/// scale, and a tap on an empty spot resolves to the quarter hour under it.
///
/// The rail is tall enough that the shortest service in the catalogue still gets
/// a card whose text is whole, and a card never shrinks below `minCardHeight`:
/// duration drives the height, but legibility sets its floor.
enum AgendaGrid {
    static let dayStartHour = 9
    static let dayEndHour = 20
    static let snapMinutes = 15
    static let hourHeight: CGFloat = 96
    static let gutterWidth: CGFloat = 46

    /// Breathing room between a card and the one below it.
    static let cardGap: CGFloat = 4

    /// Height a card needs for each of its three shapes: name row alone, name
    /// row plus one line of services, plus two. The budget is a 16pt name row,
    /// 2pt, then 14pt a line, inside 5pt of vertical padding; cards take these
    /// as a floor, so a longer font or a wider line can only push them taller,
    /// never cut a word in half.
    static let minCardHeight: CGFloat = 26
    static let oneServiceLineHeight: CGFloat = 42
    static let twoServiceLinesHeight: CGFloat = 56

    static var hours: Range<Int> { dayStartHour..<dayEndHour }
    static var dayMinutes: Int { (dayEndHour - dayStartHour) * 60 }
    static var totalHeight: CGFloat { hourHeight * CGFloat(dayEndHour - dayStartHour) }

    static func minutesFromStart(_ time: LocalTime) -> Int {
        time.minutesOfDay - dayStartHour * 60
    }

    static func y(for time: LocalTime) -> CGFloat {
        hourHeight * CGFloat(minutesFromStart(time)) / 60
    }

    static func height(minutes: Int) -> CGFloat {
        hourHeight * CGFloat(minutes) / 60
    }

    /// The height the card of a `minutes`-long appointment is given — a floor,
    /// never a ceiling.
    ///
    /// Duration sets it, but a short appointment borrows the empty time
    /// in front of it — `freeMinutesAfter`, up to the next appointment or block
    /// in the column — so even a ten-minute service shows its line in full. The
    /// card stops growing once the services fit: it never runs over what is
    /// booked next. When nothing is free after it, the card still keeps its
    /// single row whole; the few points it then overhangs sit under the next
    /// card, which is drawn over it.
    static func cardHeight(minutes: Int, freeMinutesAfter: Int = 0) -> CGFloat {
        let own = height(minutes: minutes) - cardGap
        if own >= twoServiceLinesHeight { return own }
        let grown = height(minutes: minutes + freeMinutesAfter) - cardGap
        return max(own, min(grown, twoServiceLinesHeight), minCardHeight)
    }

    /// Lines of services a card of `cardHeight` has room for: 0, 1 or 2.
    static func serviceLines(cardHeight: CGFloat) -> Int {
        if cardHeight >= twoServiceLinesHeight { return 2 }
        if cardHeight >= oneServiceLineHeight { return 1 }
        return 0
    }

    /// Start of the quarter hour at `y`, or nil outside the day.
    static func time(atY y: CGFloat) -> LocalTime? {
        let minutes = Int((y / hourHeight * 60).rounded(.down))
        let snapped = minutes / snapMinutes * snapMinutes
        guard snapped >= 0, snapped < dayMinutes else { return nil }
        return LocalTime(dayStartHour, 0).plusMinutes(snapped)
    }

    /// Minutes of empty rail between `endMinutes` and the next thing in the
    /// column, whose starts are `busyStarts` (minutes from the rail's start).
    static func freeMinutesAfter(endMinutes: Int, busyStarts: [Int]) -> Int {
        let next = busyStarts.filter { $0 >= endMinutes }.min() ?? dayMinutes
        return max(next - endMinutes, 0)
    }
}

/// Hour labels down the left edge of the timetable. The hour rules run under
/// them too, on the same background as the columns: rail and grid read as one.
struct AgendaHourLabels: View {
    var body: some View {
        VStack(spacing: 0) {
            ForEach(AgendaGrid.hours, id: \.self) { hour in
                Text(String(format: "%02d:00", hour))
                    .font(Typo.jost(11))
                    .foregroundStyle(Color.textMuted)
                    .padding(.top, 4)
                    .frame(width: AgendaGrid.gutterWidth - 8, height: AgendaGrid.hourHeight, alignment: .topLeading)
                    .padding(.leading, 8)
            }
        }
        .background(AgendaHourLines())
    }
}

/// A stretch of a column that can't be booked — off shift, lunch break,
/// holidays, a course: one grey band, label centered, all looking the same.
///
/// A band says how long the salon is shut, so it is never stretched to fit its
/// label: the label takes the lines the band has room for, and a band too thin
/// for even one goes bare rather than showing half a word.
struct AgendaUnavailableBand: View {
    let range: TimeRange
    let label: String?
    /// Nil = as wide as the column it sits in.
    var width: CGFloat?

    var body: some View {
        let minutes = range.end.minutesOfDay - range.start.minutesOfDay
        let bandHeight = AgendaGrid.height(minutes: minutes)
        let labelLines = bandHeight >= 32 ? 2 : (bandHeight >= 16 ? 1 : 0)
        Rectangle()
            .fill(Color.stoneSoft)
            .overlay {
                if let label, labelLines > 0 {
                    Text(label)
                        .font(Typo.jost(11))
                        .foregroundStyle(Color.textMuted)
                        .multilineTextAlignment(.center)
                        .lineLimit(labelLines)
                        .padding(.horizontal, 6)
                }
            }
            .frame(width: width, height: bandHeight)
            .offset(y: AgendaGrid.y(for: range.start))
            .allowsHitTesting(false)
    }
}

/// Hour rules (and fainter half-hour ones) behind a column of the timetable.
struct AgendaHourLines: View {
    var body: some View {
        VStack(spacing: 0) {
            ForEach(AgendaGrid.hours, id: \.self) { _ in
                VStack(spacing: 0) {
                    Rectangle().fill(Color.stone).frame(height: 1)
                    Spacer()
                    Rectangle().fill(Color.stone.opacity(0.5)).frame(height: 1)
                    Spacer()
                }
                .frame(height: AgendaGrid.hourHeight)
            }
        }
        .frame(height: AgendaGrid.totalHeight)
    }
}
