from playwright.async_api import async_playwright
from watchfiles import awatch

from config import BASE_DIR, chromium_launch_options
from pathlib import Path

from utils.base_social_media import set_init_script
from utils.log import zhihu_logger, xiaohongshu_logger


async def cookie_auth_zhihu(cookie_file):
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(**chromium_launch_options())
        context = await browser.new_context(storage_state=cookie_file)
        context = await set_init_script(context)
        # 创建一个新的页面
        page = await context.new_page()
        # 访问指定的URL(知乎写作的URL)
        await page.goto("https://zhuanlan.zhihu.com/write")
        try:
            # 检测五秒，查看写作的页面有没有登录相关的元素，若有，或继续往下执行（cookie文件失效），否则报错（cookie文件有效）
            await page.wait_for_selector("div.signQr-rightContainer div.SignContainer-content div.SignFlow-tabs div.SignFlow-tab:text('验证码登录')", timeout=5000)

            zhihu_logger.info("[+] 等待5秒 cookie 失效")
            return False
        except Exception as e:
            zhihu_logger.success("[+] cookie 有效")
            return True

async def cookie_auth_xiaohongshu(cookie_file):
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(**chromium_launch_options())
        context = await browser.new_context(storage_state=cookie_file)
        context = await set_init_script(context)
        # 创建一个新的页面
        page = await context.new_page()
        # 访问指定的URL(小红书写长文的URL)
        await page.goto("https://creator.xiaohongshu.com/publish/publish?from=menu&target=article")
        try:
            # 检测五秒，查看写长文的页面有没有登录相关的元素，若有，或继续往下执行（cookie文件失效），否则报错（cookie文件有效）
            await page.wait_for_selector(":text('短信登录')", timeout=5000)

            xiaohongshu_logger.info("[+] 等待5秒 cookie 失效")
            return False
        except Exception as e:
            xiaohongshu_logger.success("[+] cookie 有效")
            return True

async def cookie_auth_jinritoutiao(cookie_file):
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(**chromium_launch_options())
        context = await browser.new_context(storage_state=cookie_file)
        context = await set_init_script(context)
        # 创建一个新的页面
        page = await context.new_page()
        # 访问指定的URL(今日头条写文章的URL)
        await page.goto("https://mp.toutiao.com/profile_v4/graphic/publish")
        try:
            # 检测五秒，查看写文章页面有没有登录相关的元素，若有，或继续往下执行（cookie文件失效），否则报错（cookie文件有效）
            await page.wait_for_selector("#BD_Login_Form div.web-login-container div.web-login-union__login__form div.web-login-union__login__form__title:text('验证码登录')", timeout=5000)

            xiaohongshu_logger.info("[+] 等待5秒 cookie 失效")
            return False
        except Exception as e:
            xiaohongshu_logger.success("[+] cookie 有效")
            return True

async def cookie_auth_baijiahao(cookie_file):
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(**chromium_launch_options())
        context = await browser.new_context(storage_state=cookie_file)
        context = await set_init_script(context)
        # 创建一个新的页面
        page = await context.new_page()
        # 访问指定的URL(百家号写文章的URL)
        await page.goto("https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1")
        try:
            # 检测五秒，查看写文章页面有没有登录相关的元素，若有，或继续往下执行（cookie文件失效），否则报错（cookie文件有效）
            await page.wait_for_selector("button.startBtn--FjUA0", timeout=5000)

            xiaohongshu_logger.info("[+] 等待5秒 cookie 失效")
            return False
        except Exception as e:
            xiaohongshu_logger.success("[+] cookie 有效")
            return True

async def check_cookie(type, file_path):
    match type:
        # 知乎
        case 1:
            return await cookie_auth_zhihu(Path(BASE_DIR / "cookiesFiles" / file_path))
        # 小红书
        case 2:
            return await cookie_auth_xiaohongshu(Path(BASE_DIR / "cookiesFiles" / file_path))
        # 今日头条文章
        case 3:
            return await cookie_auth_jinritoutiao(Path(BASE_DIR / "cookiesFiles" / file_path))
        # 百家号
        case 4:
            return await cookie_auth_baijiahao(Path(BASE_DIR / "cookiesFiles" / file_path))
