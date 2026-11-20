import XCTest

/// A push notification delivered by `xcrun simctl push` and tapped in SpringBoard opens the deep-linked
/// screen: the Swift Firebase bridge (ios-bridges/firebase) → stapel-push's controller → the app's link
/// router. Runs only under `scripts/ios-smoke.sh --push`, which sends the pushes when this test says it is
/// ready (a handshake directory on the host, `STAPEL_PUSH_HANDSHAKE`); skipped otherwise.
final class PushUITests: XCTestCase {
    private let env = ProcessInfo.processInfo.environment
    private let timeout: TimeInterval = 30

    override func setUp() {
        continueAfterFailure = false
    }

    private func node(_ app: XCUIApplication, _ tag: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: tag).firstMatch
    }

    private func waitFor(_ app: XCUIApplication, _ tag: String, _ message: String) -> XCUIElement {
        let element = node(app, tag)
        XCTAssertTrue(element.waitForExistence(timeout: timeout), message)
        return element
    }

    private func type(_ app: XCUIApplication, into tag: String, _ text: String) {
        let field = waitFor(app, tag, "field \(tag)")
        let editable = field.descendants(matching: .textField).firstMatch.exists ? field.descendants(matching: .textField).firstMatch : field
        editable.tap()
        editable.typeText(text)
    }

    /// Tells the script a step is reached and waits for its answer (the push was sent).
    private func handshake(_ dir: String, _ step: String) {
        FileManager.default.createFile(atPath: "\(dir)/\(step).ready", contents: Data())
        let sent = "\(dir)/\(step).sent"
        let deadline = Date().addingTimeInterval(60)
        while !FileManager.default.fileExists(atPath: sent) && Date() < deadline { Thread.sleep(forTimeInterval: 0.5) }
        XCTAssertTrue(FileManager.default.fileExists(atPath: sent), "the script did not send the push for \(step)")
    }

    func testTappingAPushOpensItsDeepLink() throws {
        guard let dir = env["STAPEL_PUSH_HANDSHAKE"] else { throw XCTSkip("run by scripts/ios-smoke.sh --push") }
        let stand = env["STAPEL_STAND_URL"] ?? "http://127.0.0.1:18741/"
        let app = XCUIApplication()
        app.launchArguments = ["-stapelBaseUrl", stand, "-stapelLogFile", "push-markers.log"]
        app.launch()

        // Signed in, so the deep-linked order (a member screen) opens directly.
        _ = waitFor(app, "auth.sign_in", "sign-in")
        type(app, into: "auth.sign_in.identifier", "push+\(UInt32.random(in: 0...UInt32.max))@example.com")
        node(app, "auth.sign_in.send_code").tap()
        _ = waitFor(app, "auth.code.mock_hint", "prototype posture: the mock-delivery hint")
        type(app, into: "auth.code.field", "0000")
        _ = waitFor(app, "shop.home", "signed in, on home")

        // Nothing was asked at launch; the person turns push on: the system prompt, answered "Allow".
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        XCTAssertFalse(springboard.alerts.firstMatch.exists, "no notification prompt before the person asked")
        waitFor(app, "shop.home.push", "the push toggle").tap()
        let alert = springboard.alerts.firstMatch
        XCTAssertTrue(alert.waitForExistence(timeout: timeout), "the system notification prompt")
        alert.buttons.element(boundBy: alert.buttons.count - 1).tap() // "Allow", whatever the language
        let state = node(app, "shop.home.push.state")
        XCTAssertTrue(
            XCTWaiter().wait(
                for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "label CONTAINS 'Granted'"), object: state)],
                timeout: timeout
            ) == .completed,
            "permission granted: \(state.label)"
        )

        // In the background: the push arrives as a banner; tapping it brings the app to the order.
        XCUIDevice.shared.press(.home)
        handshake(dir, "background")
        let banner = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS 'Order 42 is on its way'")).firstMatch
        XCTAssertTrue(banner.waitForExistence(timeout: timeout), "the banner of the pushed notification")
        banner.tap()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: timeout), "the tap opened the app")
        _ = waitFor(app, "shop.order", "the push's deep link opened the order")
        XCTAssertTrue(app.staticTexts["Order 42"].waitForExistence(timeout: timeout), "the order id from data.url")

        // In the foreground: the push reaches the app (marker `stapel.push received … fg=true`, read by the script).
        handshake(dir, "foreground")
        Thread.sleep(forTimeInterval: 3)
    }
}
