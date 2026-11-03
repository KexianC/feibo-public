import asyncio
from pathlib import Path

from config import BASE_DIR
from postArtical.xiaohongshu.main_uploader import XHSArtical


def post_artical_xiaohongshu(title, content, text, tags, cookie_file): # 小红书需要填写正文，无封面
    # 生成cookie文件和img文件完整的路径
    cookie_file = Path(BASE_DIR / "cookiesFiles" / cookie_file)

    print(
        "发布任务已准备 platform=xiaohongshu "
        f"title_length={len(title or '')} tag_count={len(tags or [])}"
    )
    app = XHSArtical(title, content, text, tags, cookie_file) # 小红书需要填写正文
    asyncio.run(app.main(), debug=False)
