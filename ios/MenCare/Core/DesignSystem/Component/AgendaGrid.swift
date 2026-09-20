import SwiftUI

/// Day timetable shared by the owner's and the staff agenda: same hours, same
/// scale, and a tap on an empty spot resolves to the quarter hour under it.
enum AgendaGrid {
    static let dayStartHour = 9
    static let dayEndHour = 20
    static let snapMinutes = 15
    static let hourHeight: CGFloat = 64
    static let gutterWidth: CGFloat = 46

    static var hours: Range<Int> { dayStartHour..<dayEndHour }
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

    /// Start of the quarter hour at `y`, or nil outside the day.
    static func time(atY y: CGFloat) -> LocalTime? {
        let minutes = Int((y / hourHeight * 60).rounded(.down))
        let snapped = minutes / snapMinutes * snapMinutes
        guard snapped >= 0, snapped < (dayEndHour - dayStartHour) * 60 else { return nil }
        return LocalTime(dayStartHour, 0).plusMinutes(snapped)
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
struct AgendaUnavailableBand: View {
    let range: TimeRange
    let label: String?
    /// Nil = as wide as the column it sits in.
    var width: CGFloat?

    var body: some View {
        let minutes = range.end.minutesOfDay - range.start.minutesOfDay
        Rectangle()
            .fill(Color.stoneSoft)
            .overlay {
                if let label {
                    Text(label)
                        .font(Typo.jost(11))
                        .foregroundStyle(Color.textMuted)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .padding(.horizontal, 6)
                }
            }
            .frame(width: width, height: AgendaGrid.height(minutes: minutes))
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
