from typing import List
from pathlib import Path
from config import BASE_DIR

SOCIAL_MEDIA_ZHIHU = "zhihu"

def get_supported_social_media() -> List[str]:
    return [SOCIAL_MEDIA_ZHIHU]

def get_cli_action() -> List[str]:
    return ["upload", "login", "watch"]

async def set_init_script(context):
    stealth_js_path = Path(BASE_DIR / "utils/stealth.min.js") # 防止被检测自动化
    await context.add_init_script(path=stealth_js_path)
    return context