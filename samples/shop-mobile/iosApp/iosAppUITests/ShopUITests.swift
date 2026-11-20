import XCTest

/// The reference app driven through the iOS accessibility tree (Compose test tags are accessibility
/// identifiers) against a local auth stand in prototype posture (mock OTP `0000`).
///
/// The methods run in name order and share the simulator's state on purpose: a fresh install, sign-in,
/// a system deep link, the edge-swipe back, the token vault across relaunches, sign-out.
/// `scripts/ios-smoke.sh --uitest` uninstalls the app first and passes the stand's URL as
/// `TEST_RUNNER_STAPEL_STAND_URL`.
final class ShopUITests: XCTestCase {
    private static let standUrl = ProcessInfo.processInfo.environment["STAPEL_STAND_URL"] ?? "http://127.0.0.1:18741/"
    private static let email = "uitest+\(UInt32.random(in: 0...UInt32.max))@example.com"
    private let timeout: TimeInterval = 30

    override func setUp() {
        continueAfterFailure = false
    }

    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        // The app's own log records (stapel.link … opened|queued|refused) go to Documents/uitest-markers.log;
        // ios-smoke.sh keeps the file next to the result bundle.
        app.launchArguments = ["-stapelBaseUrl", Self.standUrl, "-stapelLogFile", "uitest-markers.log"]
        app.launch()
        return app
    }

    private func node(_ app: XCUIApplication, _ tag: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: tag).firstMatch
    }

    private func waitFor(_ app: XCUIApplication, _ tag: String, _ message: String) -> XCUIElement {
        let element = node(app, tag)
        XCTAssertTrue(element.waitForExistence(timeout: timeout), message)
        return element
    }

    /// Taps the centre of [element]'s on-screen frame in [app]'s coordinate space, once that frame is real.
    /// On the macos-26 runner (iOS 26.5) Safari's accessibility tree for the web view sometimes comes back
    /// half-fetched: the prompt's ancestors report the frame {{inf, inf}, {0, 0}} while the button itself
    /// has its real frame. XCUITest's own `tap()` derives hittability from the ancestors, computes the hit
    /// point {-1, -1}, synthesizes nothing and reports no failure; a tap at the button's frame lands.
    private func tapAtFrame(of element: XCUIElement, in app: XCUIApplication, _ message: String) {
        let framed = XCTNSPredicateExpectation(
            predicate: NSPredicate { object, _ in
                guard let e = object as? XCUIElement, e.exists else { return false }
                let f = e.frame
                return !f.isEmpty && !f.isInfinite && !f.isNull && f.minX.isFinite && f.minY.isFinite
            },
            object: element
        )
        XCTAssertEqual(XCTWaiter().wait(for: [framed], timeout: timeout), .completed, message)
        let f = element.frame
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: f.midX, dy: f.midY)).tap()
    }

    private func type(_ app: XCUIApplication, into tag: String, _ text: String) {
        let field = waitFor(app, tag, "field \(tag)")
        let editable = field.descendants(matching: .textField).firstMatch.exists ? field.descendants(matching: .textField).firstMatch : field
        editable.tap()
        editable.typeText(text)
    }

    /// 3a: a first launch has no session: plain sign-in, no "session expired" notice.
    func test1_freshInstallShowsPlainSignIn() {
        let app = launch()
        _ = waitFor(app, "auth.sign_in", "sign-in on first launch")
        XCTAssertFalse(node(app, "auth.sign_in.notice").exists, "a fresh install shows no notice")
    }

    /// (iii): sign in with the mock code against the stand; the vault is written (proved by test4).
    func test2_signInWithTheMockCode() {
        let app = launch()
        _ = waitFor(app, "auth.sign_in", "sign-in")
        type(app, into: "auth.sign_in.identifier", Self.email)
        node(app, "auth.sign_in.send_code").tap()
        _ = waitFor(app, "auth.code.mock_hint", "prototype posture: the mock-delivery hint")
        type(app, into: "auth.code.field", "0000")
        _ = waitFor(app, "shop.home", "signed in, on home")
    }

    /// (i) the system "Open in …?" prompt, then the deep link's destination; (ii) the edge-swipe back.
    func test3_systemDeepLinkThenEdgeSwipeBack() {
        let app = launch()
        _ = waitFor(app, "shop.home", "the stored session reopens home")
        // The link as a person follows it: typed into Safari, which asks "Open in “ShopMobile”?".
        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        safari.launch()
        let address = safari.descendants(matching: .any).matching(identifier: "TabBarItemTitle").firstMatch
        if address.waitForExistence(timeout: 10) { address.tap() } else { safari.textFields.firstMatch.tap() }
        safari.typeText("shopmobile://app/orders/42\n")
        let prompt = safari.descendants(matching: .any).matching(identifier: "SFDialogView").firstMatch
        XCTAssertTrue(prompt.waitForExistence(timeout: 10), "the system's open-in-app prompt")
        // Confirm with the prompt's last button ("Open"), whatever the simulator's language, tapped where it
        // is on screen. On a fresh simulator Safari may show its first-run tip ("View Bookmarks, Share Menu,
        // and Open Tabs") over the page at the same moment: the prompt is dimmed and the first tap only
        // dismisses the tip (seen in the CI screen recordings of 491f707 and acd4d99, iOS 26.5). So: while
        // the app has not shown the order and Safari still shows the prompt, tap Open again (bounded). The app's order
        // screen is the signal, not XCUIApplication.state: measured on CI (acd4d99), the app reported
        // "running foreground" at once while Safari's prompt was still on screen.
        let open = prompt.buttons.element(boundBy: prompt.buttons.count - 1)
        for _ in 1...3 {
            tapAtFrame(of: open, in: safari, "the prompt's Open button has an on-screen frame")
            if node(app, "shop.order").waitForExistence(timeout: 10) || !prompt.exists { break }
        }
        let order = waitFor(app, "shop.order", "the deep link opened the order")
        XCTAssertTrue(app.staticTexts["Order 42"].waitForExistence(timeout: timeout), "the order id from the link")
        // Edge swipe from the left edge: the system back gesture on a pushed screen.
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.0, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5)))
        _ = waitFor(app, "shop.home", "the stack popped back to home")
        XCTAssertFalse(order.exists, "the order screen is gone")
    }

    /// Item 5: the Keychain vault survives a relaunch and is wiped by sign-out.
    func test4_vaultSurvivesRelaunchAndSignOutWipesIt() {
        var app = launch()
        _ = waitFor(app, "shop.home", "signed in before the relaunch")
        app.terminate()
        app = launch()
        _ = waitFor(app, "shop.home", "the session survived the relaunch (read back from the Keychain)")
        node(app, "auth.account_menu").tap()
        let signOut = waitFor(app, "auth.account_menu.sign_out", "account menu")
        let signOutLabel = signOut.label
        signOut.tap()
        // The confirmation's "Sign out" (the dialog carries the tag; the menu, closed now, had the same label).
        _ = waitFor(app, "auth.sign_out.confirm", "sign-out confirmation")
        let confirm = app.buttons.matching(NSPredicate(format: "label == %@", signOutLabel)).firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: timeout), "the confirmation's sign-out button")
        confirm.tap()
        _ = waitFor(app, "auth.sign_in", "signed out")
        app.terminate()
        app = launch()
        _ = waitFor(app, "auth.sign_in", "after sign-out a relaunch has no session")
        XCTAssertFalse(node(app, "shop.home").exists)
        XCTAssertFalse(node(app, "auth.sign_in.notice").exists, "a voluntary sign-out leaves no notice on relaunch")
    }
}
