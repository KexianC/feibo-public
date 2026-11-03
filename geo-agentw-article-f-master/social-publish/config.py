import os
from pathlib import Path

BASE_DIR = Path(os.getenv("SOCIAL_PUBLISH_BASE_DIR", Path(__file__).parent.resolve())).resolve()
LOCAL_CHROME_PATH = os.getenv("LOCAL_CHROME_PATH", r"C:\Program Files\Google\Chrome\Application\chrome.exe")
LOCAL_CHROME_HEADLESS = os.getenv("LOCAL_CHROME_HEADLESS", "false").lower() == "true"


def chromium_launch_options():
    options = {"headless": LOCAL_CHROME_HEADLESS}
    chrome_path = Path(LOCAL_CHROME_PATH)
    if chrome_path.is_file():
        options["executable_path"] = str(chrome_path)
    return options
