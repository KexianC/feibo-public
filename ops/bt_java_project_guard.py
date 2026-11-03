#!/www/server/panel/pyenv/bin/python3.7
import json
import os
import sys

PANEL_ROOT = "/www/server/panel"
PROJECT_NAME = "digital-doctor-app"
EXPECTED_PROJECT_ID = 6
EXPECTED_JAR = "/www/wwwroot/digital-doctor-app.jar"

sys.path.insert(0, PANEL_ROOT)
sys.path.insert(0, os.path.join(PANEL_ROOT, "class"))
os.chdir(PANEL_ROOT)

import public  # noqa: E402
from projectModel.javaModel import main as JavaProjectModel  # noqa: E402


def load_project(model):
    project = model.get_project_find(PROJECT_NAME)
    if not project:
        raise RuntimeError("the pinned BaoTa Java project does not exist")
    if int(project.get("id")) != EXPECTED_PROJECT_ID:
        raise RuntimeError("the BaoTa Java project id changed")
    if project.get("path") != EXPECTED_JAR:
        raise RuntimeError("the BaoTa Java project JAR path changed")
    config = project.get("project_config") or {}
    if config.get("project_jar") != EXPECTED_JAR:
        raise RuntimeError("the BaoTa Java project configuration changed")
    return project


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in {"stop", "pause-monitoring", "resume-monitoring"}:
        raise RuntimeError("usage: bt_java_project_guard.py stop|pause-monitoring|resume-monitoring")

    action = sys.argv[1]
    model = JavaProjectModel()
    project = load_project(model)

    if action == "stop":
        request = public.dict_obj()
        request.project_name = PROJECT_NAME
        result = model.stop_project(request)
        if not isinstance(result, dict) or not result.get("status"):
            raise RuntimeError("BaoTa stop_project failed: {}".format(result))
        if not model.is_stop_by_user(EXPECTED_PROJECT_ID):
            raise RuntimeError("BaoTa user-stop marker was not persisted")
        print(json.dumps({"status": True, "action": action, "project_id": EXPECTED_PROJECT_ID}))
        return

    if action == "pause-monitoring":
        model.stop_by_user(EXPECTED_PROJECT_ID)
        if not model.is_stop_by_user(EXPECTED_PROJECT_ID):
            raise RuntimeError("BaoTa user-stop marker was not persisted")
    else:
        model.start_by_user(EXPECTED_PROJECT_ID)
        if model.is_stop_by_user(EXPECTED_PROJECT_ID):
            raise RuntimeError("BaoTa user-stop marker was not cleared")

    print(json.dumps({"status": True, "action": action, "project_id": EXPECTED_PROJECT_ID}))


if __name__ == "__main__":
    main()
