import asyncio
import uuid

from playwright.async_api import async_playwright

from config import BASE_DIR, chromium_launch_options
from utils.auth import check_cookie
from utils.base_social_media import set_init_script
from pathlib import Path
import sqlite3


async def xiaohongshu_cookie_gen(id,status_queue,userid):
    url_changed_event = asyncio.Event()

    async def on_url_change():
        # 检查主框架URL是否变化
        if page.url != original_url:
            url_changed_event.set()

    async with async_playwright() as playwright:
        options = chromium_launch_options()
        options['args'] = ['--lang en-GB']
        browser = await playwright.chromium.launch(**options)
        context = await browser.new_context()
        context = await set_init_script(context)
        page = await context.new_page()
        await page.goto("https://creator.xiaohongshu.com/")
        # 点击二维码登录
        await page.locator('img.css-wemwzq').click()
        # 获取二维码
        img_locator = page.get_by_role("img").nth(2)
        # 获取 src 属性值
        src = await img_locator.get_attribute("src")
        original_url = page.url
        print(
            "登录二维码已生成 "
            f"platform=xiaohongshu payload_length={len(src or '')} has_payload={bool(src)}"
        )
        status_queue.put(src)

        # 监听页面的 'framenavigated' 事件，只关注主框架的变化
        page.on('framenavigated',
                lambda frame: asyncio.create_task(on_url_change()) if frame == page.main_frame else None)

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
        print("cookie state 已创建 platform=xiaohongshu")
        # 确保cookiesFiles目录存在
        cookies_dir = Path(BASE_DIR / "cookiesFiles")
        cookies_dir.mkdir(exist_ok=True)
        # 将浏览器存储状态保存下来
        await context.storage_state(path=cookies_dir / f"{uuid_v1}.json")
        # 检查文件有没有正确保存
        result = await check_cookie(2, f"{uuid_v1}.json")
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
                           ''', (2, f"{uuid_v1}.json", id, 1, userid))
            conn.commit()
            print("✅ 用户状态已记录")
        status_queue.put("200")
