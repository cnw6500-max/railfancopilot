import SwiftUI
import MapKit
import shared

// ═══════════════════════════════════════════════════════════════════════════════
// ANDROID PARITY FEATURES
// 1. Station Board sheet
// 2. Log Transmission sheet
// 3. Classification Yards data + map annotations
// ═══════════════════════════════════════════════════════════════════════════════

// ── Classification yards (mirrors Android CLASSIFICATION_YARDS) ───────────────
let classificationYards: [ClassificationYard] = [
    // UP
    ClassificationYard(id:"yard-up-bailey",    name:"Bailey Yard (UP)",         railroad:"UP",   latitude:41.1403, longitude:-100.7601, description:"World's largest classification yard — 315 car lengths", warning:"Active mainline; private property", frequency:"161.100"),
    ClassificationYard(id:"yard-up-proviso",   name:"Proviso Yard (UP)",         railroad:"UP",   latitude:41.9003, longitude:-87.8614,  description:"UP's primary Chicago gateway yard",                   warning:"Private property",                 frequency:"160.515"),
    ClassificationYard(id:"yard-up-neff",      name:"Neff Yard (UP)",            railroad:"UP",   latitude:39.1136, longitude:-94.5572,  description:"UP Kansas City classification yard",                  warning:"Private property",                 frequency:"161.010"),
    ClassificationYard(id:"yard-up-roseville", name:"Roseville Yard (UP)",       railroad:"UP",   latitude:38.7521, longitude:-121.2880, description:"Largest UP yard on the West Coast",                   warning:"Private property",                 frequency:"160.515"),
    ClassificationYard(id:"yard-up-englewood", name:"Englewood Yard (UP)",       railroad:"UP",   latitude:29.7354, longitude:-95.2971,  description:"UP Houston gateway — heavy petrochemical traffic",    warning:"Private property",                 frequency:"160.590"),
    // BNSF
    ClassificationYard(id:"yard-bnsf-argentine",name:"Argentine Yard (BNSF)",   railroad:"BNSF", latitude:39.0864, longitude:-94.6603,  description:"BNSF's largest Kansas City facility",                 warning:"Private property",                 frequency:"160.410"),
    ClassificationYard(id:"yard-bnsf-galesburg",name:"Barr Yard / Galesburg (BNSF)",railroad:"BNSF",latitude:40.9478,longitude:-90.3712,description:"Key BNSF hub on the Transcon",                       warning:"Private property",                 frequency:"160.410"),
    ClassificationYard(id:"yard-bnsf-cicero",  name:"Cicero Yard (BNSF)",       railroad:"BNSF", latitude:41.8681, longitude:-87.7461,  description:"BNSF Chicago area hump yard",                         warning:"Private property",                 frequency:"161.385"),
    ClassificationYard(id:"yard-bnsf-alliance",name:"Alliance Yard (BNSF)",     railroad:"BNSF", latitude:32.9543, longitude:-97.4119,  description:"BNSF Fort Worth intermodal facility",                 warning:"Private property",                 frequency:"160.515"),
    // CSX
    ClassificationYard(id:"yard-csx-selkirk",  name:"Selkirk Yard (CSX)",       railroad:"CSX",  latitude:42.5537, longitude:-73.8426,  description:"CSX's largest Northeast yard near Albany NY",         warning:"Private property",                 frequency:"160.230"),
    ClassificationYard(id:"yard-csx-willard",  name:"Willard Yard (CSX)",        railroad:"CSX",  latitude:41.0548, longitude:-82.7235,  description:"Major CSX hump yard in northern Ohio",                warning:"Private property",                 frequency:"160.560"),
    ClassificationYard(id:"yard-csx-waycross", name:"Rice Yard / Waycross (CSX)",railroad:"CSX",  latitude:31.2138, longitude:-82.3579,  description:"CSX Southeast hub — busiest yard in the SE",          warning:"Private property",                 frequency:"161.070"),
    ClassificationYard(id:"yard-csx-russell",  name:"Russell Yard (CSX)",        railroad:"CSX",  latitude:38.5162, longitude:-82.6843,  description:"CSX Kentucky hub — heavy coal and manifest traffic",  warning:"Private property",                 frequency:"160.230"),
    ClassificationYard(id:"yard-csx-calumet",  name:"Calumet Yard (CSX)",        railroad:"CSX",  latitude:41.6789, longitude:-87.5876,  description:"CSX Chicago south side intermodal yard",              warning:"Private property",                 frequency:"160.410"),
    // NS
    ClassificationYard(id:"yard-ns-conway",    name:"Conway Yard (NS)",          railroad:"NS",   latitude:40.6553, longitude:-80.2418,  description:"NS Pittsburgh-area hump yard",                        warning:"Private property",                 frequency:"160.410"),
    ClassificationYard(id:"yard-ns-elkhart",   name:"Elkhart Yard (NS)",         railroad:"NS",   latitude:41.6856, longitude:-85.9669,  description:"NS Chicago-line gateway — high intermodal volume",    warning:"Private property",                 frequency:"161.070"),
    ClassificationYard(id:"yard-ns-enola",     name:"Enola Yard (NS)",           railroad:"NS",   latitude:40.2851, longitude:-76.9555,  description:"NS former PRR hump yard near Harrisburg PA",          warning:"Private property",                 frequency:"161.190"),
    ClassificationYard(id:"yard-ns-chattanooga",name:"Chattanooga Yard (NS)",    railroad:"NS",   latitude:35.0456, longitude:-85.3097,  description:"NS Southeast hub at the Tennessee Gateway",           warning:"Private property",                 frequency:"160.410"),
    // CN
    ClassificationYard(id:"yard-cn-braidwood", name:"Braidwood Yard (CN)",       railroad:"CN",   latitude:41.2553, longitude:-88.2109,  description:"CN Chicago-area classification yard",                 warning:"Private property",                 frequency:"160.410"),
    ClassificationYard(id:"yard-cn-memphis",   name:"Johnston Yard (CN)",        railroad:"CN",   latitude:35.1067, longitude:-90.0534,  description:"CN Memphis gateway — connects to IC lines",           warning:"Private property",                 frequency:"161.070"),
    // CPKC
    ClassificationYard(id:"yard-cp-bensenville",name:"Bensenville Yard (CPKC)",  railroad:"CP",   latitude:41.9575, longitude:-87.9425,  description:"CPKC Chicago gateway hump yard",                      warning:"Private property",                 frequency:"160.515"),
    ClassificationYard(id:"yard-cp-kansas-city",name:"Knoche Yard (CPKC)",       railroad:"CP",   latitude:39.1042, longitude:-94.6261,  description:"CPKC Kansas City hub — post-merger KCS traffic",      warning:"Private property",                 frequency:"160.410"),
]

// ── Station Board sheet ───────────────────────────────────────────────────────
struct StationBoardSheet: View {
    @ObservedObject var vm: RailFanViewModel
    @Environment(\.dismiss) var dismiss
    @State private var codeInput = ""
    @FocusState private var focused: Bool

    private let popularCodes = ["CHI","NYP","LAX","WAS","BOS","SEA","PDX","NOL","SAN","MKE","SAC","SLC"]

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 16) {

                        // Search row
                        HStack(spacing: 10) {
                            TextField("Station code (e.g. CHI, NYP, LAX)", text: $codeInput)
                                .textInputAutocapitalization(.characters)
                                .autocorrectionDisabled()
                                .focused($focused)
                                .foregroundColor(.textPrimary)
                                .font(.system(size: 15, design: .monospaced))
                                .onChange(of: codeInput) { new in codeInput = new.uppercased().prefix(5).description }
                                .onSubmit { search() }
                            if !codeInput.isEmpty {
                                Button { codeInput = "" } label: {
                                    Image(systemName: "xmark.circle.fill").foregroundColor(.textMuted)
                                }
                            }
                            Button { search() } label: {
                                Image(systemName: "magnifyingglass")
                                    .foregroundColor(.white).font(.system(size: 14, weight: .semibold))
                                    .padding(10).background(Color.railBlueMid).cornerRadius(8)
                            }
                            .disabled(codeInput.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                        .padding(12).background(Color.bgInput).cornerRadius(12)
                        .padding(.horizontal)

                        // Quick-pick popular stations
                        if vm.stationDepartures.isEmpty && !vm.stationDeparturesLoading {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("POPULAR STATIONS")
                                    .font(.system(size: 11, weight: .semibold)).foregroundColor(.textMuted)
                                    .padding(.horizontal)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        ForEach(popularCodes, id: \.self) { code in
                                            Button {
                                                codeInput = code
                                                vm.loadStationDepartures(code: code)
                                            } label: {
                                                Text(code)
                                                    .font(.system(size: 13, weight: .medium, design: .monospaced))
                                                    .foregroundColor(.textPrimary)
                                                    .padding(.horizontal, 12).padding(.vertical, 7)
                                                    .background(Color.bgCard)
                                                    .cornerRadius(8)
                                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.border, lineWidth: 0.5))
                                            }
                                        }
                                    }
                                    .padding(.horizontal)
                                }
                            }
                        }

                        // Loading
                        if vm.stationDeparturesLoading {
                            VStack(spacing: 12) {
                                ProgressView().scaleEffect(1.2).tint(.railBlue)
                                Text("Loading departures…").font(.system(size: 13)).foregroundColor(.textMuted)
                            }
                            .frame(maxWidth: .infinity).padding(40)
                        }

                        // Error
                        if let err = vm.stationDeparturesError {
                            VStack(spacing: 10) {
                                Image(systemName: "tram.slash").font(.system(size: 36)).foregroundColor(.textMuted)
                                Text(err).font(.system(size: 14)).foregroundColor(.textMuted).multilineTextAlignment(.center)
                            }
                            .frame(maxWidth: .infinity).padding(40)
                        }

                        // Results
                        if !vm.stationDepartures.isEmpty {
                            VStack(spacing: 0) {
                                ForEach(vm.stationDepartures) { dep in
                                    DepartureRow(dep: dep)
                                    if dep.id != vm.stationDepartures.last?.id {
                                        Divider().background(Color.border).padding(.leading, 16)
                                    }
                                }
                            }
                            .cardStyle().padding(.horizontal)
                        }

                        Spacer(minLength: 40)
                    }
                    .padding(.top)
                }
            }
            .navigationTitle("Station Board")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Done") { vm.clearStationDepartures(); dismiss() }.foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { focused = true }
        .onDisappear { vm.clearStationDepartures() }
    }

    private func search() {
        focused = false
        vm.loadStationDepartures(code: codeInput)
    }
}

struct DepartureRow: View {
    let dep: StationDeparture
    var statusColor: Color {
        let s = dep.status.lowercased()
        if s.contains("on time") || s.contains("early") { return .green }
        if s.contains("late") || s.contains("delay") { return .orange }
        return .textMuted
    }
    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(dep.scheduledDep)
                    .font(.system(size: 15, weight: .bold, design: .monospaced))
                    .foregroundColor(.railBlue)
                if let est = dep.estimatedDep, !est.isEmpty, est != dep.scheduledDep {
                    Text("Est \(est)").font(.system(size: 11)).foregroundColor(.orange)
                }
            }
            .frame(width: 62, alignment: .leading)

            VStack(alignment: .leading, spacing: 3) {
                Text(dep.trainName)
                    .font(.system(size: 13, weight: .semibold)).foregroundColor(.textPrimary)
                Text(dep.routeName)
                    .font(.system(size: 12)).foregroundColor(.textMuted)
            }
            Spacer()
            if !dep.status.isEmpty {
                Text(dep.status)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(statusColor)
                    .padding(.horizontal, 6).padding(.vertical, 3)
                    .background(statusColor.opacity(0.12)).cornerRadius(5)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
    }
}

// ── Log Transmission sheet ────────────────────────────────────────────────────
struct LogTransmissionSheet: View {
    let channel: RadioChannel
    @ObservedObject var vm: RailFanViewModel
    let onDismiss: () -> Void

    @State private var note = ""
    @State private var selectedTrainSymbol: String? = nil
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Color.bgPrimary.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {

                        // Channel header
                        HStack(spacing: 10) {
                            Image(systemName: "antenna.radiowaves.left.and.right")
                                .foregroundColor(.railBlue).font(.system(size: 18))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(channel.name)
                                    .font(.system(size: 15, weight: .semibold)).foregroundColor(.textPrimary)
                                Text(String(format: "%.4f MHz", channel.frequencyMhz))
                                    .font(.system(size: 13, design: .monospaced)).foregroundColor(.railBlue)
                            }
                        }
                        .padding(14).cardStyle().padding(.horizontal)

                        // Note input
                        VStack(alignment: .leading, spacing: 8) {
                            Text("NOTES").font(.system(size: 11, weight: .semibold)).foregroundColor(.textMuted)
                            TextEditor(text: $note)
                                .frame(minHeight: 100)
                                .foregroundColor(.textPrimary)
                                .padding(10).background(Color.bgCard).cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.border, lineWidth: 0.5))
                                .overlay(alignment: .topLeading) {
                                    if note.isEmpty {
                                        Text("What did you hear?")
                                            .foregroundColor(.textMuted).font(.system(size: 14)).padding(14)
                                    }
                                }
                        }
                        .padding(.horizontal)

                        // Tag to nearby train
                        if !vm.filteredTrains.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("TAG TO TRAIN").font(.system(size: 11, weight: .semibold)).foregroundColor(.textMuted)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        ForEach(vm.filteredTrains.prefix(10)) { train in
                                            let isSelected = selectedTrainSymbol == train.symbol
                                            Button { selectedTrainSymbol = isSelected ? nil : train.symbol } label: {
                                                HStack(spacing: 6) {
                                                    Image(systemName: "train.side.front.car")
                                                        .font(.system(size: 11))
                                                        .foregroundColor(isSelected ? .white : .textMuted)
                                                    Text(train.symbol)
                                                        .font(.system(size: 13, weight: isSelected ? .bold : .regular))
                                                        .foregroundColor(isSelected ? .white : .textPrimary)
                                                }
                                                .padding(.horizontal, 10).padding(.vertical, 7)
                                                .background(isSelected ? Color.railBlueMid : Color.bgCard)
                                                .cornerRadius(8)
                                                .overlay(RoundedRectangle(cornerRadius: 8)
                                                    .stroke(isSelected ? Color.railBlue : Color.border, lineWidth: 0.5))
                                            }
                                        }
                                    }
                                    .padding(.horizontal)
                                }
                            }
                        }

                        // Log button
                        Button {
                            vm.logTransmission(
                                channelName: channel.name,
                                frequencyMhz: channel.frequencyMhz,
                                note: note.trimmingCharacters(in: .whitespacesAndNewlines),
                                trainSymbol: selectedTrainSymbol
                            )
                            onDismiss()
                            dismiss()
                        } label: {
                            Label("Log Transmission", systemImage: "checkmark.circle.fill")
                                .font(.system(size: 16, weight: .semibold)).foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 15)
                                .background(Color.railBlueMid).cornerRadius(12)
                        }
                        .padding(.horizontal)

                        // Recent log
                        if !vm.transmissionLog.isEmpty {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("RECENT LOGS")
                                    .font(.system(size: 11, weight: .semibold)).foregroundColor(.textMuted)
                                ForEach(vm.transmissionLog.prefix(5)) { entry in
                                    VStack(alignment: .leading, spacing: 4) {
                                        HStack {
                                            Text(entry.channelName)
                                                .font(.system(size: 12, weight: .medium)).foregroundColor(.railBlue)
                                            Spacer()
                                            Text(entry.timestamp, style: .relative)
                                                .font(.system(size: 11)).foregroundColor(.textMuted)
                                        }
                                        if !entry.note.isEmpty {
                                            Text(entry.note).font(.system(size: 13)).foregroundColor(.textSecondary)
                                        }
                                        if let sym = entry.trainSymbol {
                                            Label(sym, systemImage: "train.side.front.car")
                                                .font(.system(size: 11)).foregroundColor(.textMuted)
                                        }
                                    }
                                    .padding(10).background(Color.bgInput).cornerRadius(8)
                                }
                            }
                            .padding(.horizontal)
                        }

                        Spacer(minLength: 40)
                    }
                    .padding(.top)
                }
            }
            .navigationTitle("Log Transmission")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { onDismiss(); dismiss() }.foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

// ── Yard annotation for MKMapView ─────────────────────────────────────────────
final class YardMKAnnotation: NSObject, MKAnnotation {
    let yard: ClassificationYard
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: yard.latitude, longitude: yard.longitude)
    }
    init(_ y: ClassificationYard) { yard = y }
}

// ── Yard detail sheet ─────────────────────────────────────────────────────────
struct YardDetailSheet: View {
    let yard: ClassificationYard
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Map(coordinateRegion: .constant(MKCoordinateRegion(
                        center: CLLocationCoordinate2D(latitude: yard.latitude, longitude: yard.longitude),
                        span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05))))
                    .frame(height: 160).cornerRadius(12).padding(.horizontal)

                    VStack(alignment: .leading, spacing: 6) {
                        Text(yard.name).font(.system(size: 20, weight: .bold)).foregroundColor(.textPrimary)
                        Label(yard.railroad, systemImage: "tram.fill")
                            .font(.system(size: 13)).foregroundColor(.textMuted)
                    }
                    .padding(.horizontal)

                    VStack(spacing: 0) {
                        DetailRow(label: "Scanner", value: "\(yard.frequency) MHz")
                        DetailRow(label: "Access",  value: yard.warning)
                    }
                    .cardStyle().padding(.horizontal)

                    VStack(alignment: .leading, spacing: 8) {
                        Label("About", systemImage: "building.2").foregroundColor(.railBlue)
                            .font(.system(size: 14, weight: .semibold)).foregroundColor(.textPrimary)
                        Text(yard.description).font(.system(size: 13)).foregroundColor(.textSecondary).lineSpacing(4)
                    }
                    .padding(14).cardStyle().padding(.horizontal)

                    Button {
                        let url = URL(string: "maps://?ll=\(yard.latitude),\(yard.longitude)&q=\(yard.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")")!
                        UIApplication.shared.open(url)
                    } label: {
                        Label("Get Directions", systemImage: "arrow.triangle.turn.up.right.circle")
                            .font(.system(size: 15, weight: .medium)).foregroundColor(.railBlue)
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(Color.bgCard).cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.border, lineWidth: 0.5))
                    }
                    .padding(.horizontal)

                    Spacer(minLength: 40)
                }
                .padding(.top)
            }
            .background(Color.bgPrimary)
            .navigationTitle("Classification Yard")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.bgPrimary, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }.foregroundColor(.railBlue)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
