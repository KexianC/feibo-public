import asyncio

from playwright.async_api import Playwright, async_playwright

from config import chromium_launch_options
from utils.base_social_media import set_init_script
from utils.log import jinritoutiao_logger


class JRTTArtical(object):
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
        await page.goto('https://mp.toutiao.com/profile_v4/graphic/publish')
        # 等待页面跳转到指定的URL，没进入，则自动等待超时
        jinritoutiao_logger.info('正在打开发布作品页面-------')
        await page.wait_for_url('https://mp.toutiao.com/profile_v4/graphic/publish')
        # 若果有首发激励弹窗，需要先把弹窗关闭,检测3秒
        try:
            await page.wait_for_selector('img.banner-image', timeout=1000)
            jinritoutiao_logger.info('检测到首发激励弹窗，正在关闭---------')
            # 点击关闭按钮
            await page.locator('button.byte-modal-close-icon').click()
        except Exception as e:
            jinritoutiao_logger.info('未检测到首发激励弹窗，即将进入下一步操作-------')
            pass
        # 尝试关闭可能出现的遮罩层（点击页面任意位置）
        try:
            # 等待遮罩出现（短超时，避免长时间等待）
            await page.wait_for_selector('.byte-drawer-mask', timeout=1000)
            # 点击页面左上角关闭遮罩
            await page.mouse.click(10, 10)
            # 等待遮罩消失
            await page.wait_for_selector('.byte-drawer-mask', state='hidden', timeout=1000)
            jinritoutiao_logger.info('成功关闭遮罩层')
        except:
            jinritoutiao_logger.info('未找到遮罩层')
            pass  # 如果没有遮罩，继续执行
        # 开始上传封面
        if self.img:
            jinritoutiao_logger.info(
                '正在上传封面 platform=jinritoutiao has_cover={}',
                bool(self.img),
            )
            # 点击单图上传
            await page.locator('span.byte-radio-inner-text:has-text("单图")').click()
            await page.locator('div.article-cover-add').click()
            # 点击上传按钮进行上传
            upload_input = page.locator('input[type="file"]').nth(0)
            await upload_input.set_input_files(self.img)
            await asyncio.sleep(2)
            # 点击确定按钮
            await page.locator('[data-e2e="imageUploadConfirm-btn"]').click()
            jinritoutiao_logger.info('封面上传完成')
        else:
            jinritoutiao_logger.info('用户未选择封面图片，选择无封面按钮。')
            await page.locator('span.byte-radio-inner-text:has-text("无封面")').click()

        # 点击不投放广告按钮
        try:
            await page.wait_for_selector('span.byte-radio-inner-text:has-text("不投放广告")',timeout=300)
            await page.locator('span.byte-radio-inner-text:has-text("不投放广告")').click()
            jinritoutiao_logger.info('成功点击不投放广告选择')
        except Exception as e:
            jinritoutiao_logger.info('未找到不投放广告选择')
            pass

        # 点击引用ai和健康医疗分享，仅供参考选项
        await page.locator('span.byte-checkbox-inner-text:has-text("引用AI")').click()
        await page.locator('span.byte-checkbox-inner-text:has-text("健康医疗分享，仅供参考")').click()


        # 开始填充标题，内容，话题，话题
        # 填充标题，根据placeholder来获取，需要时刻注意页面元素的变化
        await page.get_by_placeholder("请输入文章标题（2～30个字）").click()
        jinritoutiao_logger.info('成功点击标题的textarea--------')
        jinritoutiao_logger.info('开始清除已有标题----------')
        await page.keyboard.press('Backspace')
        await page.keyboard.press('Control+KeyA')
        await page.keyboard.press('Delete')
        jinritoutiao_logger.info('开始填充标题----------')
        await page.keyboard.type(self.title)
        await page.keyboard.press('Enter')

        # 开始填充正文
        await page.locator('div.ProseMirror').click()
        jinritoutiao_logger.info('成功点击正文的组件----------')
        jinritoutiao_logger.info('开始清除已有正文----------')
        await page.keyboard.press('Backspace')
        await page.keyboard.press('Control+KeyA')
        await page.keyboard.press('Delete')
        jinritoutiao_logger.info('开始填充正文-------')
        await page.keyboard.type(self.content)
        await page.keyboard.press('Enter')

        # 开始填充话题
        for index, tag in enumerate(self.tags, start=1):
            jinritoutiao_logger.info('正在添加第%s个话题'%index)
            await page.keyboard.type("#"+tag)
            # 先等待，获取搜索出来话题的第一个，完成话题填充
            await page.wait_for_timeout(1000)
            await page.locator('section.forum-list-item').first.click()
            # await asyncio.sleep(1)

        # ---------- 发布文章 ----------
        jinritoutiao_logger.info("正在点击发布按钮...")
        publish_button = page.get_by_text("预览并发布", exact=True)
        await publish_button.click()
        await page.wait_for_selector('span:has-text("确认发布")', state='visible')
        await page.locator('span:has-text("确认发布")').click()
        # 等待发布成功提示（最多等待 5 秒）
        try:
            await page.wait_for_url('https://mp.toutiao.com/profile_v4/graphic/articles', timeout=5000)
            jinritoutiao_logger.success("文章发布成功！")
        except Exception as e:
            jinritoutiao_logger.error(
                "发布超时或失败 platform=jinritoutiao exception_type={}",
                type(e).__name__,
            )
            # 可选：截图保存现场
            await page.screenshot(path="publish_failed.png", full_page=True)
            raise

        await context.storage_state(path=self.cookie_file) # 保存cookie
        jinritoutiao_logger.success('cookie文件更新完毕！')
        await asyncio.sleep(2) # 延迟关闭
        # 关闭浏览器上下文和浏览器实例
        await context.close()
        await browser.close()

    async def main(self):
        async with async_playwright() as playwright:
            await self.upload(playwright)
