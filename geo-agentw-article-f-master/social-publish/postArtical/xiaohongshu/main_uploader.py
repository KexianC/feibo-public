from playwright.async_api import Playwright, async_playwright
from config import chromium_launch_options
from utils.base_social_media import set_init_script
from utils.log import xiaohongshu_logger
import asyncio


class XHSArtical(object):
    def __init__(self, title, content, text, tags, cookie_file):
        self.title = title
        self.content = content
        self.text = text
        self.tags = tags
        self.cookie_file = cookie_file
        # self.date_format = '%Y-%m-%d %H:%M:%S'

    async def upload(self, playwright: Playwright) -> None:
        # 使用 Chrome 浏览器启动一个浏览器实例
        browser = await playwright.chromium.launch(**chromium_launch_options())
        # 创建一个浏览器上下文，使用指定的 cookie 文件
        context = await browser.new_context(
            viewport={'width': 1600, 'height': 900},
            storage_state=f"{self.cookie_file}"
        )
        context = await set_init_script(context)

        # 创建一个新的页面
        page = await context.new_page()
        # 访问指定的 URL
        await page.goto("https://creator.xiaohongshu.com/publish/publish?from=menu&target=article")
        # 点击"新的创作"按钮
        await page.locator('.new-btn').click();
        xiaohongshu_logger.success("进入小红书长文新的创作成功！")
        xiaohongshu_logger.info("[+] 开始填充标题内容----------")
        await page.get_by_placeholder('输入标题').fill(self.title);
        xiaohongshu_logger.info("[+] 开始填充内容----------------")
        await page.locator('p[data-placeholder="输入文字，内容将自动保存"]').click();
        await page.keyboard.type(self.content);
        xiaohongshu_logger.info("[+] 点击一键排版按钮---------------")
        await page.locator('.next-btn').click();
        # 排版完成选择时，使用简约
        await page.wait_for_selector("span.template-title:has-text('简约基础')", state="visible")
        await page.click("span.template-title:has-text('简约基础')")
        await page.wait_for_timeout(2000)  # 等待 2 秒，确保模板应用
        # 点击提交按钮
        await page.wait_for_selector('.submit', state= 'visible');
        await page.click('.submit');
        # 填充内容及话题，话题使用 #+文字表示
        css_selector_p = ".tiptap > p"  # ".ql-editor" # 不能加上 .ql-blank 属性，这样只能获取第一次非空状态
        css_selector = ".tiptap"
        await page.wait_for_selector(css_selector_p, state = 'visible')
        await page.locator(css_selector_p).click()
        await page.keyboard.press("Backspace")
        await page.keyboard.press("Control+KeyA")
        await page.keyboard.press("Delete")
        await page.keyboard.type(self.text)

        for index, tag in enumerate(self.tags, start=0):
            await page.type(css_selector, "#" + tag)
            await page.wait_for_timeout(1000)
            await page.press(css_selector, "Space")
            # await page.keyboard.press("Enter")
        xiaohongshu_logger.info(f'总共添加{len(self.tags)}个话题')

        # ---------- 发布文章 ----------
        xiaohongshu_logger.info("正在点击发布按钮...")
        publish_button = page.get_by_text("发布", exact=True)
        await publish_button.click()

        # 等待发布成功提示（最多等待 5 秒）
        try:
            await page.wait_for_url(
                "https://creator.xiaohongshu.com/publish/success?**",
                timeout=5000
            )  # 如果自动跳转到作品页面，则代表发布成功
        except Exception as e:
            xiaohongshu_logger.error(
                "发布超时或失败 platform=xiaohongshu exception_type={}",
                type(e).__name__,
            )
            # 可选：截图保存现场
            await page.screenshot(path="publish_failed.png", full_page=True)
            raise

        await context.storage_state(path=self.cookie_file)  # 保存cookie
        xiaohongshu_logger.success('cookie文件更新完毕！')
        await asyncio.sleep(2)  # 延迟关闭
        # 关闭浏览器上下文和浏览器实例
        await context.close()
        await browser.close()

    async def main(self):
        async with async_playwright() as playwright:
            await self.upload(playwright)
