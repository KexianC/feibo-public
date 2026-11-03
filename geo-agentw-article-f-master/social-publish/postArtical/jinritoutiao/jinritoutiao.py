import asyncio
from pathlib import Path

from config import BASE_DIR
from postArtical.jinritoutiao.main_uploader import JRTTArtical


def post_artical_jinritoutiao(title, content, img, tags, cookie_file):
    # 生成cookie文件和img文件完整的路径
    cookie_file = Path(BASE_DIR / "cookiesFiles" / cookie_file)
    if img:
        img = Path(BASE_DIR / "imgFiles" / img)

    print(
        "发布任务已准备 platform=jinritoutiao "
        f"title_length={len(title or '')} tag_count={len(tags or [])} has_cover={bool(img)}"
    )
    app = JRTTArtical(title, content, img, tags, cookie_file)
    asyncio.run(app.main(), debug=False)
