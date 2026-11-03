import asyncio

from playwright.async_api import Playwright, async_playwright

from config import chromium_launch_options
from utils.base_social_media import set_init_script
from utils.log import zhihu_logger


class ZHArtical(object):
    def __init__(self, title, content, img, tags, cookie_file):
        self.title = title
        self.content = content
        self.img = img
        self.tags = tags
        self.cookie_file = cookie_file
        # self.date_format = '%Y-%m-%d %H:%M:%S'

    async def upload(self, playwright: Playwright) -> None:
        # 使用 Chrome 浏览器启动一个浏览器实例
        browser = await playwright.chromium.launch(**chromium_launch_options())
        # 开一个新的浏览器
        context = await browser.new_context(storage_state=f"{self.cookie_file}")
        context = await set_init_script(context)
        # 创建一个新的页面
        page = await context.new_page()
        # 访问指定的URL
        await page.goto('https://zhuanlan.zhihu.com/write')
        # 等待页面跳转到指定的URL，没进入，则自动等待超时
        zhihu_logger.info('正在打开发布作品页面-------')
        await page.wait_for_url('https://zhuanlan.zhihu.com/write')
        zhihu_logger.info('正在上传封面 platform=zhihu has_cover={}', bool(self.img))
        # 点击 "上传封面图片" input
        # upload_input = page.locator("input[class^='UploadPicture-input']")
        # # await upload_input.wait_for(state='visible') # 确保按钮可见
        #
        # async with page.expect_file_chooser() as fc_info:
        #     await upload_input.click()
        #
        # file_chooser = await fc_info.value
        # await file_chooser.set_files(self.img)
        upload_input = page.locator("input[class^='UploadPicture-input']")
        await upload_input.set_input_files(self.img)
        zhihu_logger.info('封面上传完成')

        await asyncio.sleep(2)

        # 开始填充标题，内容，话题，话题最多填充三个，只取前三个
        # 填充标题，根据placeholder来获取，需要时刻注意页面元素的变化
        await page.get_by_placeholder("请输入标题（最多 100 个字）").click()
        zhihu_logger.info('成功点击标题的textarea--------')
        zhihu_logger.info('开始清除已有标题----------')
        await page.keyboard.press('Backspace')
        await page.keyboard.press('Control+KeyA')
        await page.keyboard.press('Delete')
        zhihu_logger.info('开始填充标题----------')
        await page.keyboard.type(self.title)
        await page.keyboard.press('Enter')

        # 开始填充正文
        await page.locator('.DraftEditor-root [contenteditable="true"]').click()
        zhihu_logger.info('成功点击正文的组件----------')
        zhihu_logger.info('开始清除已有正文----------')
        await page.keyboard.press('Backspace')
        await page.keyboard.press('Control+KeyA')
        await page.keyboard.press('Delete')
        zhihu_logger.info('开始填充正文-------')
        await page.keyboard.type(self.content)
        await page.keyboard.press('Enter')

        # 开始填充话题，知乎最多填充3个话题
        for index, tag in enumerate(self.tags[:3], start=1):
            zhihu_logger.info('正在添加第%s个话题'%index)
            await page.get_by_text("添加话题", exact=True).click()
            input_box = page.locator('input[aria-label="搜索话题"]').first
            await input_box.fill(tag)
            # 获取搜索出来话题的第一个，完成话题填充
            await page.locator('div.css-ogem9c > button').first.wait_for()
            await page.locator('div.css-ogem9c > button').first.click()
            await asyncio.sleep(1)

        # ---------- 发布文章 ----------
        zhihu_logger.info("正在点击发布按钮...")
        publish_button = page.get_by_text("发布", exact=True)
        await publish_button.click()

        # 等待发布成功提示（最多等待 5 秒）
        try:
            success_indicator = page.get_by_text("发布成功", exact=True)
            await success_indicator.wait_for(state="visible", timeout=5000)
            zhihu_logger.success("文章发布成功！")
        except Exception as e:
            zhihu_logger.error(
                "发布超时或失败 platform=zhihu exception_type={}",
                type(e).__name__,
            )
            # 可选：截图保存现场
            await page.screenshot(path="publish_failed.png", full_page=True)
            raise

        await context.storage_state(path=self.cookie_file) # 保存cookie
        zhihu_logger.success('cookie文件更新完毕！')
        await asyncio.sleep(2) # 延迟关闭
        # 关闭浏览器上下文和浏览器实例
        await context.close()
        await browser.close()

    async def main(self):
        async with async_playwright() as playwright:
            await self.upload(playwright)
