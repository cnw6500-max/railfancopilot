import XCTest

final class ScreenshotTests: XCTestCase {
    let app = XCUIApplication()

    override func setUpWithError() throws {
        continueAfterFailure = false
        app.launch()
        sleep(6)
    }

    func testAllScreens() throws {
        snap("01_map")

        tap("Scanner"); snap("02_scanner")
        tap("Decoder");  snap("03_decoder")
        tap("Photo");    snap("04_photo")
        tap("More");     sleep(1); snap("05_more_list")

        // From More list, tap each item
        tapCell("Community"); snap("06_community"); goBack()
        tapCell("Roster");    snap("07_roster");    goBack()
        tapCell("Webcams");   snap("08_webcams");   goBack()
        tapCell("Spots");     snap("09_spots");     goBack()
        tapCell("Trip Log");  snap("10_triplog");   goBack()
        tapCell("Settings");  snap("11_settings")
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
