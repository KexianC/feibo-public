import asyncio

from playwright.async_api import Playwright, async_playwright

from config import chromium_launch_options
from utils.base_social_media import set_init_script
from utils.log import baijiahao_logger


class BJHArtical(object):
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
        await page.goto('https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1')
        # 等待页面跳转到指定的URL，没进入，则自动等待超时
        baijiahao_logger.info('正在打开发布作品页面-------')
        await page.wait_for_url('https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1')
        # 若果有功能升级提示弹窗，需要先把弹窗关闭,检测1秒
        try:
            await page.wait_for_selector('div.cheetah-tour-content', timeout=1000)
            baijiahao_logger.info('检测到功能升级提示弹窗，正在关闭---------')
            # 点击关闭按钮
            await page.locator('button.cheetah-tour-close').click()
        except Exception as e:
            baijiahao_logger.info('未检测到功能升级提示弹窗，即将进入下一步操作-------')
            pass
        # 开始上传封面
        baijiahao_logger.info('正在上传封面 platform=baijiahao has_cover={}', bool(self.img))
        # 点击单图上传
        await page.get_by_text("单图", exact=True).click()
        await page.get_by_text("选择封面", exact=True).click()
        # 点击上传按钮进行上传
        upload_input = page.locator('span.cheetah-upload > input')
        await upload_input.set_input_files(self.img)
        await asyncio.sleep(2)
        # 点击确定按钮
        await page.get_by_text("确定", exact=False).click()
        baijiahao_logger.info('封面上传完成')

        # 后续智能创作两个框的按钮的代码可以放在这里写


        # 创作声明 勾选采用AI生成内容
        await page.locator('span.aigc_bjh_status:has-text("采用AI生成内容")').click()

        # 开始填充标题，内容，话题，话题
        # 开始填充标题
        await page.locator('div[data-testid="news-title-input"]').click()
        baijiahao_logger.info('成功点击标题的组件--------')
        baijiahao_logger.info('开始清除已有标题----------')
        await page.keyboard.press('Backspace')
        await page.keyboard.press('Control+KeyA')
        await page.keyboard.press('Delete')
        baijiahao_logger.info('开始填充标题----------')
        await page.keyboard.type(self.title)
        await page.keyboard.press('Enter')

        # 开始填充正文
        await page.locator('p[data-diagnose-id]').click()
        baijiahao_logger.info('成功点击正文的组件----------')
        baijiahao_logger.info('开始清除已有正文----------')
        await page.keyboard.press('Backspace')
        await page.keyboard.press('Control+KeyA')
        await page.keyboard.press('Delete')
        baijiahao_logger.info('开始填充正文-------')
        await page.keyboard.type(self.content)
        await page.keyboard.press('Enter')

        # 开始填充话题(测试最多只能添加一个话题)
        for index, tag in enumerate(self.tags[0], start=1):
            baijiahao_logger.info('正在添加第%s个话题'%index)
            await page.keyboard.type("#"+tag)
            # 先等待，获取搜索出来话题的第一个，完成话题填充
            await page.wait_for_timeout(1000)
            await page.locator('span.topic-text').first.click()
            # await asyncio.sleep(1)

        # ---------- 发布文章 ----------
        baijiahao_logger.info("正在点击发布按钮...")
        publish_button = page.get_by_text("发布", exact=True)
        await publish_button.click()
        # 等待发布成功提示（最多等待 5 秒）
        try:
            await page.wait_for_url(lambda url: '/builder/rc/clue' in url, timeout=5000)
            baijiahao_logger.success("文章发布成功！")
        except Exception as e:
            baijiahao_logger.error(
                "发布超时或失败 platform=baijiahao exception_type={}",
                type(e).__name__,
            )
            # 可选：截图保存现场
            await page.screenshot(path="publish_failed.png", full_page=True)
            raise

        await context.storage_state(path=self.cookie_file) # 保存cookie
        baijiahao_logger.success('cookie文件更新完毕！')
        await asyncio.sleep(2) # 延迟关闭
        # 关闭浏览器上下文和浏览器实例
        await context.close()
        await browser.close()

    async def main(self):
        async with async_playwright() as playwright:
            await self.upload(playwright)
