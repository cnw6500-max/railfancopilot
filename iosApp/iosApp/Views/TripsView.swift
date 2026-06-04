import SwiftUI

struct TripsView: View {
    @ObservedObject var vm: RailFanViewModel
    @State private var showEndSheet = false
    @State private var notesInput = ""
    @State private var alightingInput = ""

    private let dateFmt: DateFormatter = {
        let f = DateFormatter(); f.dateStyle = .medium; f.timeStyle = .short; return f
    }()

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 12) {

                        // ── Stats row ─────────────────────────────────────────
                        HStack(spacing: 0) {
                            TripStatCell(label: "Miles",
                                         value: String(format: "%.1f", vm.totalTripMiles),
                                         icon: "speedometer")
                            Divider().frame(height: 40).background(Color.border)
                            TripStatCell(label: "Trips",
                                         value: "\(vm.completedTrips.count)",
                                         icon: "train.side.front.car")
                            Divider().frame(height: 40).background(Color.border)
                            TripStatCell(label: "Hours",
                                         value: String(format: "%.1f", vm.totalTripHours),
                                         icon: "clock")
                        }
                        .padding(16)
                        .background(Color.bgCard)
                        .cornerRadius(14)
                        .overlay(RoundedRectangle(cornerRadius: 14)
                            .stroke(Color.borderLight, lineWidth: 0.5))
                        .padding(.horizontal)

                        // ── Active trip banner ────────────────────────────────
                        if let trip = vm.activeTrip {
                            VStack(spacing: 10) {
                                HStack(spacing: 10) {
                                    Circle().fill(Color.green).frame(width: 10, height: 10)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Riding \(trip.trainSymbol)")
                                            .font(.system(size: 14, weight: .semibold))
                                            .foregroundColor(.green)
                                        Text("\(String(format: "%.1f", trip.distanceMiles)) mi · \(trip.durationMinutes) min")
                                            .font(.system(size: 12))
                                            .foregroundColor(.textMuted)
                                    }
                                    Spacer()
                                }
                                Button {
                                    notesInput = ""
                                    alightingInput = ""
                                    showEndSheet = true
                                } label: {
                                    HStack {
                                        Image(systemName: "stop.fill")
                                        Text("End Trip")
                                    }
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(.green)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(Color.green.opacity(0.15))
                                    .cornerRadius(10)
                                }
                            }
                            .padding(14)
                            .background(Color(red: 0.06, green: 0.17, blue: 0.09))
                            .cornerRadius(14)
                            .overlay(RoundedRectangle(cornerRadius: 14)
                                .stroke(Color.green.opacity(0.4), lineWidth: 1))
                            .padding(.horizontal)
                        }

                        // ── Completed trips ───────────────────────────────────
                        if vm.completedTrips.isEmpty && vm.activeTrip == nil {
                            VStack(spacing: 8) {
                                Image(systemName: "train.side.front.car")
                                    .font(.system(size: 32)).foregroundColor(.textMuted)
                                Text("No trips yet")
                                    .foregroundColor(.textPrimary)
                                    .font(.system(size: 16, weight: .medium))
                                Text("Tap \"Ride this Train\" on any live train to start logging")
                                    .foregroundColor(.textMuted)
                                    .font(.system(size: 13))
                                    .multilineTextAlignment(.center)
                            }
                            .padding(32)
                        } else if !vm.completedTrips.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Completed Trips (\(vm.completedTrips.count))")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(.textMuted)
                                    .padding(.horizontal)

                                ForEach(vm.completedTrips) { trip in
                                    TripCard(trip: trip) { vm.deleteTrip(id: trip.id) }
                                }
                            }
                        }

                        Spacer(minLength: 80)
                    }
                    .padding(.top)
                }
            }
            .navigationTitle("Trip Log")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
        .sheet(isPresented: $showEndSheet) {
            EndTripSheet(vm: vm, notes: $notesInput, alighting: $alightingInput) {
                vm.endTrip(notes: notesInput.nilIfEmpty, alightingStation: alightingInput.nilIfEmpty)
                showEndSheet = false
            }
        }
    }
}

// ── Sub-views ─────────────────────────────────────────────────────────────────

struct TripStatCell: View {
    let label: String; let value: String; let icon: String
    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon).font(.system(size: 16)).foregroundColor(.railBlue)
            Text(value).font(.system(size: 18, weight: .semibold)).foregroundColor(.textPrimary)
            Text(label).font(.system(size: 11)).foregroundColor(.textMuted)
        }
        .frame(maxWidth: .infinity)
    }
}

struct TripCard: View {
    let trip: TripLogSwift
    let onDelete: () -> Void
    @State private var showDelete = false

    private let dateFmt: DateFormatter = {
        let f = DateFormatter(); f.dateStyle = .medium; f.timeStyle = .short; return f
    }()

    var body: some View {
        HStack(spacing: 12) {
            Rectangle()
                .fill(Color.railBlue.opacity(0.6))
                .frame(width: 4)
                .cornerRadius(2)

            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(trip.trainSymbol)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.textPrimary)
                    Text("·").foregroundColor(.textMuted)
                    Text(trip.railroad).font(.system(size: 12)).foregroundColor(.textMuted)
                }
                HStack(spacing: 4) {
                    Text("\(String(format: "%.1f", trip.distanceMiles)) mi")
                    if trip.durationMinutes > 0 {
                        Text("·").foregroundColor(.textMuted)
                        let h = trip.durationMinutes / 60, m = trip.durationMinutes % 60
                        Text(h > 0 ? "\(h)h \(m)m" : "\(m)m")
                    }
                }
                .font(.system(size: 12)).foregroundColor(.textSecondary)

                if let b = trip.boardingStation, let a = trip.alightingStation {
                    Text("\(b) → \(a)")
                        .font(.system(size: 11)).foregroundColor(.textSecondary)
                        .lineLimit(1)
                } else if let b = trip.boardingStation {
                    Text("From: \(b)").font(.system(size: 11)).foregroundColor(.textSecondary)
                } else if let a = trip.alightingStation {
                    Text("To: \(a)").font(.system(size: 11)).foregroundColor(.textSecondary)
                }

                if let n = trip.notes {
                    Text(n).font(.system(size: 11)).foregroundColor(.textMuted).lineLimit(1)
                }
                Text(dateFmt.string(from: trip.startDate))
                    .font(.system(size: 11)).foregroundColor(.textMuted)
            }

            Spacer()

            if showDelete {
                Button(action: onDelete) {
                    Image(systemName: "trash").foregroundColor(.red).font(.system(size: 16))
                }
            }
        }
        .padding(14)
        .background(Color.bgCard)
        .cornerRadius(12)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.borderLight, lineWidth: 0.5))
        .padding(.horizontal)
        .onTapGesture { showDelete.toggle() }
    }
}

struct EndTripSheet: View {
    @ObservedObject var vm: RailFanViewModel
    @Binding var notes: String
    @Binding var alighting: String
    let onConfirm: () -> Void

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                VStack(spacing: 16) {
                    if let trip = vm.activeTrip {
                        HStack(spacing: 20) {
                            VStack { Text("\(String(format: "%.1f", trip.distanceMiles))").font(.system(size: 24, weight: .bold)).foregroundColor(.textPrimary); Text("miles").font(.system(size: 12)).foregroundColor(.textMuted) }
                            Divider().frame(height: 40)
                            VStack { Text("\(trip.durationMinutes)").font(.system(size: 24, weight: .bold)).foregroundColor(.textPrimary); Text("minutes").font(.system(size: 12)).foregroundColor(.textMuted) }
                        }
                        .padding(.top)
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Alighting station (optional)").font(.system(size: 12)).foregroundColor(.textMuted)
                        TextField("e.g. New York Penn Station", text: $alighting)
                            .padding(10).background(Color.bgInput).cornerRadius(8)
                            .foregroundColor(.textPrimary)
                    }.padding(.horizontal)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Notes (optional)").font(.system(size: 12)).foregroundColor(.textMuted)
                        TextField("Trip notes...", text: $notes, axis: .vertical)
                            .lineLimit(3).padding(10).background(Color.bgInput).cornerRadius(8)
                            .foregroundColor(.textPrimary)
                    }.padding(.horizontal)
                    Button(action: onConfirm) {
                        Text("Save Trip").font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white).frame(maxWidth: .infinity)
                            .padding(.vertical, 14).background(Color.green.opacity(0.8)).cornerRadius(12)
                    }.padding(.horizontal)
                    Spacer()
                }
            }
            .navigationTitle("End Trip").navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }
}

private extension String {
    var nilIfEmpty: String? { trimmingCharacters(in: .whitespaces).isEmpty ? nil : self }
}
