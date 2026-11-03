# SZDoctor V8.3 模型头像

- 上线目标：阿里云 OSS `oss://digital-doctor-oss/ai-model-logo/hospital-logos-20260721/`。
- 数据库使用 `https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/ai-model-logo/hospital-logos-20260721/szdoctor-dept-NN.png`，不依赖前端静态目录。
- `hospital-logos-preview-20260721.png` 是 25 个头像的校对总览。
- `medtalent-general.png` 为木吾木同 MedTalent 通用模型头像，保持原有资源。

## 医院 Logo 与网页来源

| 编码 | 对应机构 | 来源 |
| --- | --- | --- |
| 01 | 浙江医院 | [官网](https://www.zjhospital.com.cn/) · [原图](https://www.zjhospital.com.cn/Public/Style/WebIndex/Default/images/logo.gif) |
| 02–04 | 浙江省人民医院 | [官网](https://www.hospitalstar.com/) · [原图](https://www.hospitalstar.com/logo.png) |
| 05 | 浙江省肿瘤医院 | [官网](https://www.zchospital.com/cn/index) · [原图](https://www.zchospital.com/cn-static/images/logo.png) |
| 06 | 浙江省立同德医院 | [官网](https://www.zjtongde.com/) · [原图](https://www.zjtongde.com/static/web/images/logo1.png) |
| 07–10 | 浙江省疾病预防控制中心 | [官网](https://www.cdc.zj.cn/) · [原图](https://www.cdc.zj.cn/images/header_words.png) |
| 11 | 浙江大学医学院附属妇产科医院 | [官网](https://www.womanhospital.cn/) · [原图](https://www.womanhospital.cn/images/fac.png) |
| 12 | 浙江大学医学院附属儿童医院 | [官网](https://www.zjuch.cn/main) · [原图](https://www.zjuch.cn/public/cn/images/logo.png) |
| 13 | 浙江大学医学院附属口腔医院 | [官网](https://www.zjuss.cn/) · [备用图（维基媒体，非院方直链）](https://upload.wikimedia.org/wikipedia/zh/f/f3/Stomatology_hospital_affiliated_to_Zhejiang_University_of_Medicine_.jpg) |
| 14 | 浙江中医药大学附属第一医院（浙江省中医院） | [官网](https://www.zjhtcm1931.cn/) · [原图](https://www.zjhtcm1931.cn/static/web/images/logo1.png) |
| 15 | 浙江中医药大学附属第二医院（浙江省新华医院） | [官网](https://www.xhhos.com/main) · [原图](https://www.xhhos.com/public/cn/images/logo.png) |
| 16 | 温州医科大学附属眼视光医院 | [官网](https://www.wzeye.cn/) · [原图](https://www.wzeye.cn/templates/waiwang_new/images/logo_foot.png) |
| 17 | 浙江大学医学院附属第二医院 | [官网](https://www.z2hospital.com/) · [原图](https://www.z2hospital.com/images/logo.png) |
| 18 | 浙江中医药大学附属第三医院（浙江省中山医院） | [官网](https://www.zjszsyy.com/home) · [原图](https://www.zjszsyy.com/static/cn/images/logo.png) |
| 19 | 温州医科大学附属第一医院 | [官网](https://www.wzhospital.cn/) · [原图](https://www.wzhospital.cn/static/cn/images/logo.png) |
| 20 | 温州医科大学附属第二医院 | [官网](https://www.wzhealth.com/) · [原图（官方旧站）](https://old.wzhealth.com/templates/waiwang/images/logo.png) |
| 21 | 浙江省皮肤病医院 | [官网](https://zjspfbyy.cn/index) · [原图](https://zjspfbyy.cn/static/web/images/logo1.png) |
| 22 | 浙江大学医学院附属邵逸夫医院 | [官网](https://www.srrsh.com/index) · [原图](https://www.srrsh.com/static/web/images/logo1.png) |
| 23 | 浙江省护理学会 | [官网文章](https://www.zjshlxh.cn/pension-gov-home/article/91521382784974850) · [原图](https://www.zjshlxh.cn/pension-gov-home/templates/1/default/_files/img/logo.png) |
| 24 | 浙江体育科学研究所 | 未找到可独立核验的机构专属 Logo，保留“运动促进健康”类别图标，未用省体育局 Logo 代替。 |
| 25 | 截图未列牵头单位 | 保留“药学健康科普”类别图标，避免臆测所属医院。 |

## 处理与使用说明

- 官网横版字标仅裁取院徽部分，统一制作为 512×512 RGBA PNG；白色透明院徽增加同色系圆形底，以保证白色卡片中可见。
- 02–04 共用浙江省人民医院院徽，07–10 共用浙江省疾控院徽，与截图中的牵头单位对应。
- 13 为当前唯一非院方图片直链的备用素材；院方官网可正常访问，但其防护页未暴露可复用的官方 Logo 直链，正式发布前建议由院方提供原始院徽替换。
- 24、25 使用原有类别图标，不冒用其他机构标识。
- 原有同名 SVG 保留为类别图标源文件；01–23 的同名 PNG 已替换为医院/机构头像，24–25 的 PNG 为类别兜底头像。
- 医院名称及标识通常受机构品牌规范和知识产权约束；本清单记录公开网页来源，正式对外发布前仍应核验院方授权和最新视觉规范。

## 2026-07-21 生产上线记录

- 25 张头像已上传至阿里云 OSS：`https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/ai-model-logo/hospital-logos-20260721/`。
- 生产数据库已应用 `replace_sharding_with_szdoctor_v83.sql`，垂类名称、头像和通用模型顺序已验证。
- 服务器已部署 SZDoctor 名称映射单类补丁并完成应用重启。
- 服务器回滚资料：`/www/wwwroot/deploy-backups/szdoctor-hospital-logos-20260721-163127/`。
- 前端 `public` / `dist` 不保存这批头像，客户端直接使用数据库返回的 OSS URL。
