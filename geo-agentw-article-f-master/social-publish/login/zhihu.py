import asyncio
import uuid
from utils.base_social_media import set_init_script
from pathlib import Path
from playwright.async_api import async_playwright
import sqlite3
import base64

from config import BASE_DIR, chromium_launch_options
from utils.auth import check_cookie

async def zhihu_cookie_gen(id,status_queue,userid):
    url_changed_event = asyncio.Event()

    async def on_url_change():
        # 检查是否是主框架的变化
        if page.url != original_url:
            url_changed_event.set()

    async with async_playwright() as playwright:
        options = chromium_launch_options()
        options['args'] = ['--lang en-GB']
        browser = await playwright.chromium.launch(**options)
        context = await browser.new_context()
        # 将stealth.min.js加载到context中，这个包主要是用来隐藏自动化特征的
        context = await set_init_script(context)
        page = await context.new_page()
        await page.goto("https://www.zhihu.com/creator")

        # 进入页面会直接显示登录二维码，不需要额外点击操作
        # canvas容器展示的二维码
        # 1.等待二维码出现
        await page.wait_for_selector('.Qrcode-qrcode', timeout=10000)
        # 2.获取Base64图片(控制台执行语句)
        qrcode = await page.wait_for_selector('.Qrcode-qrcode')
        screenshot_bytes = await qrcode.screenshot()
        src = 'data:image/png;base64,' + base64.b64encode(screenshot_bytes).decode()
        # 3.将src放入队列，通过sse返回给前端
        original_url = src
        print(
            "登录二维码已生成 "
            f"platform=zhihu payload_length={len(src)} has_payload={bool(src)}"
        )
        status_queue.put(src)

        # 监听页面的 'framenavigated' 事件，只关注主框架的变化
        page.on('framenavigated', lambda frame: asyncio.create_task(on_url_change()) if frame == page.main_frame else None)

        try:
            # 等待 URL 变化或超时,最多等待200秒即超时
            await asyncio.wait_for(url_changed_event.wait(), timeout=200)
            print("监听页面跳转成功")
        except asyncio.TimeoutError:
            status_queue.put("500")
            print("监听页面跳转超时")
            await page.close()
            await context.close()
            await browser.close()
            return None

        # 将登录成功的cookie保存在cookieFiles，以备后续使用
        uuid_v1 = uuid.uuid4()
        print("cookie state 已创建 platform=zhihu")
        # 确保cookiesFiles目录存在
        cookies_dir = Path(BASE_DIR / "cookiesFiles")
        cookies_dir.mkdir(exist_ok=True)
        # 将浏览器存储状态保存下来
        await context.storage_state(path=cookies_dir / f"{uuid_v1}.json")
        # 检查文件有没有正确保存
        result = await check_cookie(1, f"{uuid_v1}.json")
        if not result:
            status_queue.put("500")
            await page.close()
            await context.close()
            await browser.close()
            return None
        await page.close()
        await context.close()
        await browser.close()
        # 存入信息到sqlite
        with sqlite3.connect(Path(BASE_DIR / "db" / "database.db")) as conn:
            cursor = conn.cursor()
            cursor.execute('''
                           INSERT INTO user_info (type, filePath, userName, status, user_id)
                           VALUES (?, ?, ?, ?, ?)
                           ''', (1, f"{uuid_v1}.json", id, 1, userid))
            conn.commit()
            print("✅ 用户状态已记录")
        status_queue.put("200")
