import asyncio
import uuid
import sqlite3
from playwright.async_api import async_playwright

from config import BASE_DIR, chromium_launch_options
from utils.auth import check_cookie
from utils.base_social_media import set_init_script
from pathlib import Path

async def baijiahao_cookie_gen(id,status_queue,userid):
    url_changed_event = asyncio.Event()

    async def on_url_change():
        # 检查主框架是否变化
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
        await page.goto("https://baijiahao.baidu.com/builder/theme/bjh/login")

        # 进入页面后需要先点击登录或注册按钮，选择使用百家号扫码进行登录
        # 1.点击按钮
        dlbutton = await page.wait_for_selector('button.startBtn--FjUA0')
        await dlbutton.click()
        # 2.获取登录二维码的img
        qrcode = await page.wait_for_selector('img.tang-pass-qrcode-img', timeout=5000)
        # 3.将登录二维码传入前端供用户扫码登录
        src = await qrcode.get_attribute('src')
        original_url = page.url
        print(
            "登录二维码已生成 "
            f"platform=baijiahao payload_length={len(src or '')} has_payload={bool(src)}"
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
        print("cookie state 已创建 platform=baijiahao")
        # 确保cookiesFiles目录存在
        cookies_dir = Path(BASE_DIR / "cookiesFiles")
        cookies_dir.mkdir(exist_ok=True)
        # 将浏览器存储状态保存下来
        await context.storage_state(path=cookies_dir / f"{uuid_v1}.json")
        # 检查文件有没有正确保存
        result = await check_cookie(4, f"{uuid_v1}.json")
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
                               ''', (4, f"{uuid_v1}.json", id, 1, userid))
            conn.commit()
            print("✅ 用户状态已记录")
        status_queue.put("200")
