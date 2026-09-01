import XCTest

final class NewFeaturesTests: XCTestCase {
    let app = XCUIApplication()

    override func setUpWithError() throws {
        continueAfterFailure = false
        app.launch()
        sleep(6)
    }

    func testNewFeatures() throws {
        // ── Spots tab with + button ───────────────────────────────────────────
        tap("More"); sleep(1)
        tapCell("Spots"); sleep(1)
        snap("spots_list")

        // Tap + to open AddSpotSheet
        app.navigationBars["Spots"].buttons["Add"].tap(); sleep(1)
        snap("spots_add_sheet")
        app.navigationBars["Add Spot"].buttons["Cancel"].tap(); sleep(1)

        // Tap a curated spot detail
        let firstSpot = app.tables.cells.firstMatch
        if firstSpot.exists { firstSpot.tap(); sleep(1); snap("spot_detail"); goBack() }

        goBack() // back to More

        // ── Map → tap an Amtrak train for timetable ───────────────────────────
        tap("Map"); sleep(3)
        snap("map_loaded")

        // Tap the Amtrak filter chip to isolate Amtrak trains
        let amtrakChip = app.buttons["Amtrak"]
        if amtrakChip.exists { amtrakChip.tap(); sleep(1); snap("map_amtrak_filter") }

        // Tap the first train annotation visible
        let mapView = app.otherElements.firstMatch
        mapView.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        sleep(2)
        snap("train_detail_open")

        // If sheet opened, scroll down to timetable
        let sheet = app.sheets.firstMatch
        if !sheet.exists {
            // It's a modal sheet via .sheet()
            let scrollView = app.scrollViews.firstMatch
            if scrollView.exists {
                scrollView.swipeUp(); sleep(1)
                snap("train_detail_timetable")
            }
        }
    }

    private func tap(_ label: String) {
        app.tabBars.firstMatch.buttons[label].tap(); sleep(1)
    }
    private func tapCell(_ label: String) {
        let cell = app.tables.cells.containing(.staticText, identifier: label).firstMatch
        if cell.exists { cell.tap(); sleep(1) }
    }
    private func goBack() {
        let back = app.navigationBars.buttons.firstMatch
        if back.exists { back.tap(); sleep(1) }
    }
    private func snap(_ name: String) {
        let att = XCTAttachment(screenshot: app.screenshot())
        att.name = name; att.lifetime = .keepAlways; add(att)
    }
}
