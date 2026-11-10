"""Browser interaction smoke test at the industrial panel's 960x540 dp viewport."""

from pathlib import Path

from playwright.sync_api import sync_playwright


root = Path(__file__).resolve().parent
shots = root / "screenshots"
shots.mkdir(exist_ok=True)
url = (root / "index.html").as_uri()
chrome = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"


def check(condition, message):
    if not condition:
        raise AssertionError(message)


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(executable_path=chrome, headless=True)
    page = browser.new_page(viewport={"width": 960, "height": 540}, device_scale_factor=2)
    page.set_default_timeout(5000)
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url)
    page.wait_for_timeout(500)
    check(page.locator("#view-title").inner_text() == "您好，我在这里", "standby title")
    page.screenshot(path=str(shots / "01-standby-1920x1080.png"))

    page.get_by_role("button", name="体验语音唤醒").click()
    check("王爷爷" in page.locator("#view-title").inner_text(), "known user greeting")
    page.locator("#console-toggle").click()
    page.get_by_role("button", name="七条命令").click()
    for width in (960, 1200, 420):
        page.set_viewport_size({"width": width, "height": 540})
        fits = page.evaluate("""() => {
          const panel = document.querySelector('#console-panel').getBoundingClientRect();
          return [...document.querySelectorAll('#console-body .action-grid button')]
            .every(button => {
              const rect = button.getBoundingClientRect();
              return rect.left >= panel.left && rect.right <= panel.right - 2;
            });
        }""")
        check(fits, f"command buttons overflow panel at {width}px CSS viewport")
    page.set_viewport_size({"width": 960, "height": 540})
    page.wait_for_timeout(300)
    page.screenshot(path=str(shots / "07-console-1920x1080.png"))
    page.get_by_role("button", name="开始产气", exact=True).click()
    check("产气" in page.locator("#view-title").inner_text(), "gas start")
    page.locator("#console-close").click()
    page.wait_for_timeout(300)
    page.screenshot(path=str(shots / "02-producing-1920x1080.png"))

    page.locator("#console-toggle").click()
    page.get_by_role("button", name="七条命令").click()
    page.get_by_role("button", name="开始雾化", exact=True).click()
    check("雾化进行中" in page.locator("#view-detail").inner_text(), "nebulizer starts during gas")
    page.get_by_role("button", name="停止雾化", exact=True).click()
    check("雾化 关闭" in page.locator("#view-detail").inner_text(), "nebulizer stops")
    page.locator("#console-body").get_by_role("button", name="暂停产气", exact=True).click()
    check("暂停产气" in page.locator("#view-title").inner_text(), "gas paused")
    page.locator("#console-body").get_by_role("button", name="恢复产气", exact=True).click()
    check("正在为您产气" in page.locator("#view-title").inner_text(), "gas resumed")
    page.get_by_role("button", name="停止产气", exact=True).click()
    check(page.locator("#modal-backdrop").is_visible(), "stop confirmation shown")
    page.get_by_role("button", name="确认执行").click()
    check("王爷爷" in page.locator("#view-title").inner_text(), "stop gas")
    page.get_by_role("button", name="开始雾化", exact=True).click()
    check("需要先开始产气" in page.locator("#view-detail").inner_text(), "precondition rejection")

    page.get_by_role("button", name="场景", exact=True).click()
    page.get_by_role("button", name="新用户", exact=True).click()
    page.get_by_role("button", name="人脸驻留 3 秒").click()
    page.get_by_role("button", name="七条命令").click()
    page.get_by_role("button", name="开始产气", exact=True).click()
    check("称呼您" in page.locator("#view-detail").inner_text(), f"new user collection after gas start: {page.locator('#view-detail').inner_text()}")
    page.locator("#console-close").click()
    page.wait_for_timeout(300)
    page.screenshot(path=str(shots / "03-new-user-1920x1080.png"))
    page.get_by_role("button", name="爷爷", exact=True).click()
    page.locator("#age-input").fill("72")
    page.get_by_role("button", name="确认年龄").click()
    page.get_by_role("button", name="愿意提供").click()
    page.get_by_role("button", name="慢阻肺").click()
    check("72" in page.locator("#view-detail").inner_text(), "profile readback")
    page.get_by_role("button", name="确认并完成").click()
    check("正在为您产气" in page.locator("#view-title").inner_text(), "collection does not stop gas")

    page.locator("#console-toggle").click()
    page.get_by_role("button", name="场景", exact=True).click()
    page.get_by_role("button", name="网络连接失败").click()
    check("网络连接失败" in page.locator("#view-title").inner_text(), "fault shown")
    page.locator("#console-close").click()
    page.wait_for_timeout(300)
    page.screenshot(path=str(shots / "04-fault-1920x1080.png"))
    page.locator("#console-toggle").click()
    page.get_by_role("button", name="七条命令").click()
    page.get_by_role("button", name="开始产气", exact=True).click()
    check("网络连接失败" in page.locator("#view-title").inner_text(), "fault blocks commands")
    page.get_by_role("button", name="场景", exact=True).click()
    page.locator("#console-body").get_by_role("button", name="模拟故障解除").click()
    page.get_by_role("button", name="被动播报").click()
    page.get_by_role("button", name="产气完成").click()
    check("设备主动提醒" in page.locator("#view-eyebrow").inner_text(), "passive broadcast")
    page.locator("#console-close").click()
    page.get_by_role("button", name="模拟用户插话").click()
    check("设备主动提醒" not in page.locator("#view-eyebrow").inner_text(), "interrupt stops broadcast")

    page.locator("#console-toggle").click()
    page.get_by_role("button", name="七条命令").click()
    page.get_by_role("button", name="睡眠模式", exact=True).click()
    check(page.locator("#modal-backdrop").is_visible(), "sleep confirmation shown")
    page.get_by_role("button", name="确认执行").click()
    check(page.locator("#sleep-screen").is_visible(), "sleep overlay shown")
    page.screenshot(path=str(shots / "05-sleep-1920x1080.png"))
    page.get_by_role("button", name="模拟唤醒").click()
    page.locator("#console-close").click()
    page.wait_for_timeout(300)
    check("王爷爷" in page.locator("#view-title").inner_text(), "wake from sleep")

    check(not errors, f"browser errors: {errors}")
    check(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"), "horizontal overflow at 960dp")
    page.set_viewport_size({"width": 960, "height": 600})
    page.screenshot(path=str(shots / "06-fullscreen-1920x1200.png"))
    check(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"), "horizontal overflow at full screen")
    browser.close()

print("PASS: wake, seven commands, preconditions, confirmation, collection, fault, passive report, interruption, sleep, 1920x1080/1200 viewport; no page errors")
