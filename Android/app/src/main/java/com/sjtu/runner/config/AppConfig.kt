package com.sjtu.runner.config

object AppConfig {
    const val VERSION_NAME = "4.5.0"

    const val GITHUB_REPO = "JackyTJie/SJTURunningMan_Advanced_Edition"
    const val GITHUB_REPO_URL = "https://github.com/$GITHUB_REPO"
    const val GITHUB_RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO/releases"
    const val SHUIYUAN_TOPIC_URL = "https://shuiyuan.sjtu.edu.cn/t/topic/421786"

    const val HOST = "pe.sjtu.edu.cn"
    const val UID_URL = "https://pe.sjtu.edu.cn/sports/my/uid"
    const val MY_DATA_URL = "https://pe.sjtu.edu.cn/sports/my/data"
    const val POINT_RULE_URL = "https://pe.sjtu.edu.cn/api/running/point-rule"
    const val UPLOAD_URL = "https://pe.sjtu.edu.cn/api/running/result/upload"

    const val MAX_RUN_TIMES = 30
    const val MAX_DISTANCE_KM = 5.0
    const val MIN_DISTANCE_KM = 1.0
    const val DEFAULT_DISTANCE_KM = 4.0
    const val DEFAULT_RUN_HOUR = 8
    const val DEFAULT_RUN_MINUTE = 0
    const val DEFAULT_RUN_SECOND = 0
    const val INTERVAL_SECONDS = 3

    const val START_LATITUDE = 31.031599
    const val START_LONGITUDE = 121.442938

    const val DISCLAIMER_TEXT =
        "本项目仅用于学习、研究和技术测试，主要用于理解运动数据记录与上传相关的技术流程。\n\n" +
            "项目开发者反对任何违反学校规定的使用方式。\n\n" +
            "请在使用前确认自己的行为符合上海交通大学校规及相关法律法规的要求，" +
            "严禁用于伪造运动数据或规避体育锻炼要求。"

    const val COMMITMENT_TEXT =
        "在继续使用本软件前，请认真阅读并作出承诺：\n\n" +
            "1. 我理解本项目仅用于学习、研究和技术测试，主要用于理解运动数据记录与上传相关的技术流程。\n\n" +
            "2. 我理解项目开发者反对任何违反学校规定的使用方式。\n\n" +
            "3. 我承诺在使用前确认自己的行为符合上海交通大学校规及相关法律法规的要求。\n\n" +
            "4. 我承诺不会将本软件用于伪造运动数据、规避体育锻炼要求或其他违规操作。"
}
