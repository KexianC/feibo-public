-- Replace the original 24 vertical models with the 25 SZDoctor V8.3 models,
-- then add the MedTalent general model. This migration is idempotent.
-- Runtime prerequisites: SZDOCTOR_ENABLED=true and SZDOCTOR_API_KEY set securely.

START TRANSACTION;

SET @general_root_biz_id = 1970339278848659457;
SET @vertical_root_biz_id = 1970339280186642433;
SET @medtalent_category_biz_id = 9002500000000000100;
SET @medtalent_leaf_biz_id = 9002500000000000101;
SET @pharmacy_leaf_biz_id = 9002500000000000025;
SET @model_code = 'szdoctor';
SET @szdoctor_logo_base = 'https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/ai-model-logo/hospital-logos-20260721';
SET @medtalent_logo = 'https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/ai-model-logo/medtalent-general-20260720.png';

SET @general_root_id = (
    SELECT id FROM t_ai_model_config
    WHERE ai_model_config_id = @general_root_biz_id LIMIT 1
);
SET @vertical_root_id = (
    SELECT id FROM t_ai_model_config
    WHERE ai_model_config_id = @vertical_root_biz_id LIMIT 1
);

UPDATE t_ai_model_config
SET parent_id = @vertical_root_id,
    is_leaf = 1,
    type = 0,
    model_code = @model_code,
    name = CASE ai_model_config_id
        WHEN 1970339282199908354 THEN '01老年健康科普分库'
        WHEN 1970339282296377345 THEN '02眼底病与白内障健康科普分库'
        WHEN 1970339282392846338 THEN '03头颈甲状腺外科科普分库'
        WHEN 1970339282485121026 THEN '04乳腺外科科普分库'
        WHEN 1970339282585784321 THEN '05肿瘤防治科普分库'
        WHEN 1970339282678059009 THEN '06心理健康科普分库'
        WHEN 1970339282778722305 THEN '07结核病防治科普分库'
        WHEN 1970339282887774210 THEN '08艾滋病防治科普分库'
        WHEN 1970339282954883074 THEN '09免疫规划科普分库'
        WHEN 1970339283084906498 THEN '10控烟科普分库'
        WHEN 1970339283152015361 THEN '11妇女保健科普分库'
        WHEN 1970339283282038786 THEN '12儿童保健科普分库'
        WHEN 1970339283349147650 THEN '13口腔保健科普分库'
        WHEN 1970339283479171074 THEN '14中医保健科普分库'
        WHEN 1970339283542085634 THEN '15慢病（体重）管理科普分库'
        WHEN 1970339283676303361 THEN '16视觉健康科普分库'
        WHEN 1970339283743412225 THEN '17慢性心血管疾病管理科普分库'
        WHEN 1970339283873435649 THEN '18颈肩腰腿病科普分库'
        WHEN 1970339283936350209 THEN '19肿瘤与营养科普分库'
        WHEN 1970339284066373634 THEN '20骨科健康科普分库'
        WHEN 1970339284133482498 THEN '21皮肤病与医学美容科普分库'
        WHEN 1970339284263505922 THEN '22呼吸健康科普分库'
        WHEN 1970339284326420481 THEN '23护理科普分库'
        WHEN 1970339284456443905 THEN '24运动促进健康科普分库'
        ELSE name
    END,
    description = CASE ai_model_config_id
        WHEN 1970339282199908354 THEN '老年常见健康问题、营养、跌倒预防与多病共存管理科普'
        WHEN 1970339282296377345 THEN '眼底病、白内障、视网膜与黄斑相关健康科普'
        WHEN 1970339282392846338 THEN '甲状腺、头颈部肿块与颈部淋巴结相关科普'
        WHEN 1970339282485121026 THEN '乳腺结节、乳腺筛查与乳房症状科普'
        WHEN 1970339282585784321 THEN '肿瘤预防、筛查、治疗常识与随访科普'
        WHEN 1970339282678059009 THEN '心理压力、情绪、睡眠与危机提醒'
        WHEN 1970339282778722305 THEN '结核病预防、筛查、检测与规范就诊科普'
        WHEN 1970339282887774210 THEN 'HIV暴露、检测、阻断与防护科普'
        WHEN 1970339282954883074 THEN '疫苗接种、补种与接种后反应科普'
        WHEN 1970339283084906498 THEN '戒烟、控烟、二手烟与尼古丁依赖科普'
        WHEN 1970339283152015361 THEN '妇女全生命周期保健科普'
        WHEN 1970339283282038786 THEN '儿童生长发育、喂养与常见症状科普'
        WHEN 1970339283349147650 THEN '口腔卫生、龋齿、牙周与正畸科普'
        WHEN 1970339283479171074 THEN '中医保健、体质调养与安全边界科普'
        WHEN 1970339283542085634 THEN '糖尿病、血脂、体重与生活方式管理科普'
        WHEN 1970339283676303361 THEN '近视防控、干眼、视疲劳与配镜科普'
        WHEN 1970339283743412225 THEN '高血压、冠心病、房颤、心衰与胸痛胸闷科普'
        WHEN 1970339283873435649 THEN '颈椎、腰椎、肩膝关节与康复动作科普'
        WHEN 1970339283936350209 THEN '肿瘤患者营养支持与治疗期饮食科普'
        WHEN 1970339284066373634 THEN '骨折扭伤、关节、韧带与骨质疏松科普'
        WHEN 1970339284133482498 THEN '皮肤常见病、过敏、痤疮与医美安全边界科普'
        WHEN 1970339284263505922 THEN '咳嗽、哮喘、慢阻肺、肺炎与呼吸困难科普'
        WHEN 1970339284326420481 THEN '居家护理、伤口护理、压疮预防与康复护理科普'
        WHEN 1970339284456443905 THEN '运动方式选择、运动损伤预防与慢病运动科普'
        ELSE description
    END,
    image = NULL,
    sort = CAST(RIGHT(CASE ai_model_config_id
        WHEN 1970339282199908354 THEN '01' WHEN 1970339282296377345 THEN '02'
        WHEN 1970339282392846338 THEN '03' WHEN 1970339282485121026 THEN '04'
        WHEN 1970339282585784321 THEN '05' WHEN 1970339282678059009 THEN '06'
        WHEN 1970339282778722305 THEN '07' WHEN 1970339282887774210 THEN '08'
        WHEN 1970339282954883074 THEN '09' WHEN 1970339283084906498 THEN '10'
        WHEN 1970339283152015361 THEN '11' WHEN 1970339283282038786 THEN '12'
        WHEN 1970339283349147650 THEN '13' WHEN 1970339283479171074 THEN '14'
        WHEN 1970339283542085634 THEN '15' WHEN 1970339283676303361 THEN '16'
        WHEN 1970339283743412225 THEN '17' WHEN 1970339283873435649 THEN '18'
        WHEN 1970339283936350209 THEN '19' WHEN 1970339284066373634 THEN '20'
        WHEN 1970339284133482498 THEN '21' WHEN 1970339284263505922 THEN '22'
        WHEN 1970339284326420481 THEN '23' WHEN 1970339284456443905 THEN '24'
        ELSE '00' END, 2) AS UNSIGNED),
    update_time = NOW()
WHERE ai_model_config_id IN (
    1970339282199908354,1970339282296377345,1970339282392846338,1970339282485121026,
    1970339282585784321,1970339282678059009,1970339282778722305,1970339282887774210,
    1970339282954883074,1970339283084906498,1970339283152015361,1970339283282038786,
    1970339283349147650,1970339283479171074,1970339283542085634,1970339283676303361,
    1970339283743412225,1970339283873435649,1970339283936350209,1970339284066373634,
    1970339284133482498,1970339284263505922,1970339284326420481,1970339284456443905
);

INSERT INTO t_ai_model_config (
    ai_model_config_id,parent_id,is_leaf,name,description,type,model_code,image,sort,create_time,update_time
)
SELECT @pharmacy_leaf_biz_id,@vertical_root_id,1,'25药学健康科普分库',
       '药品说明书理解、用药安全、不良反应、相互作用与特殊人群用药科普',
       0,@model_code,NULL,25,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_ai_model_config WHERE ai_model_config_id=@pharmacy_leaf_biz_id);

UPDATE t_ai_model_config
SET parent_id=@vertical_root_id,is_leaf=1,name='25药学健康科普分库',
    description='药品说明书理解、用药安全、不良反应、相互作用与特殊人群用药科普',
    type=0,model_code=@model_code,image=NULL,sort=25,update_time=NOW()
WHERE ai_model_config_id=@pharmacy_leaf_biz_id;

INSERT INTO t_ai_model_config (
    ai_model_config_id,parent_id,is_leaf,name,description,type,model_code,image,sort,create_time,update_time
)
SELECT @medtalent_category_biz_id,@general_root_id,0,'木吾木同·MedTalent 健康科普大模型',
       '木吾木同·MedTalent 健康科普大模型',NULL,NULL,NULL,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_ai_model_config WHERE ai_model_config_id=@medtalent_category_biz_id);

UPDATE t_ai_model_config
SET parent_id=@general_root_id,is_leaf=0,name='木吾木同·MedTalent 健康科普大模型',
    description='木吾木同·MedTalent 健康科普大模型',type=NULL,model_code=NULL,image=NULL,sort=1,update_time=NOW()
WHERE ai_model_config_id=@medtalent_category_biz_id;

SET @medtalent_category_id = (
    SELECT id FROM t_ai_model_config WHERE ai_model_config_id=@medtalent_category_biz_id LIMIT 1
);

INSERT INTO t_ai_model_config (
    ai_model_config_id,parent_id,is_leaf,name,description,type,model_code,image,sort,create_time,update_time
)
SELECT @medtalent_leaf_biz_id,@medtalent_category_id,1,'木吾木同·MedTalent 健康科普大模型',
       '健康科普与就医提醒，不构成诊断、处方或治疗建议',0,@model_code,NULL,NULL,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_ai_model_config WHERE ai_model_config_id=@medtalent_leaf_biz_id);

UPDATE t_ai_model_config
SET parent_id=@medtalent_category_id,is_leaf=1,name='木吾木同·MedTalent 健康科普大模型',
    description='健康科普与就医提醒，不构成诊断、处方或治疗建议',
    type=0,model_code=@model_code,image=NULL,sort=NULL,update_time=NOW()
WHERE ai_model_config_id=@medtalent_leaf_biz_id;

-- Keep image metadata idempotent and cache-safe on future migration reruns.
UPDATE t_ai_model_config
SET image=CONCAT(@szdoctor_logo_base, '/szdoctor-dept-', LPAD(sort, 2, '0'), '.png'),
    update_time=NOW()
WHERE parent_id=@vertical_root_id AND BINARY model_code = BINARY @model_code AND sort BETWEEN 1 AND 25;

UPDATE t_ai_model_config
SET image=@medtalent_logo,update_time=NOW()
WHERE ai_model_config_id IN (@medtalent_category_biz_id,@medtalent_leaf_biz_id);

-- Keep the nine general-model categories in the product-defined display order.
-- Match aliases as well as Chinese display names so this remains safe across existing databases.
UPDATE t_ai_model_config
SET sort = CASE
        WHEN name LIKE '%木吾木同%MedTalent%' THEN 1
        WHEN name LIKE '%安诊儿%' THEN 2
        WHEN UPPER(name) LIKE '%GLM%' THEN 3
        WHEN name LIKE '%百川%' OR LOWER(name) LIKE '%baichuan%' THEN 4
        WHEN name LIKE '%智诊%' THEN 5
        WHEN LOWER(name) LIKE '%deepseek%' THEN 6
        WHEN name LIKE '%豆包%' OR LOWER(name) LIKE '%doubao%' THEN 7
        WHEN name LIKE '%腾讯混元%' OR UPPER(name) LIKE '%HY3%' THEN 8
        WHEN name LIKE '%通义%' OR LOWER(name) LIKE '%qwen%' THEN 9
        ELSE sort
    END,
    update_time = NOW()
WHERE parent_id = @general_root_id
  AND (is_leaf = 0 OR is_leaf IS NULL)
  AND (
      name LIKE '%木吾木同%MedTalent%' OR name LIKE '%安诊儿%' OR UPPER(name) LIKE '%GLM%'
      OR name LIKE '%百川%' OR LOWER(name) LIKE '%baichuan%' OR name LIKE '%智诊%'
      OR LOWER(name) LIKE '%deepseek%' OR name LIKE '%豆包%' OR LOWER(name) LIKE '%doubao%'
      OR name LIKE '%腾讯混元%' OR UPPER(name) LIKE '%HY3%' OR name LIKE '%通义%' OR LOWER(name) LIKE '%qwen%'
  );
-- Normalize the nine general-model category display names exactly as specified.
UPDATE t_ai_model_config
SET name = CASE ai_model_config_id
        WHEN 9002500000000000100 THEN '木吾木同·MedTalent 健康科普大模型'
        WHEN 2026505489033986050 THEN '蚂蚁-安诊儿'
        WHEN 9005200000000000000 THEN 'GLM'
        WHEN 1970687514398605965 THEN '百川'
        WHEN 1970339280480243713 THEN '智诊'
        WHEN 1970339280287305729 THEN 'DeepSeek'
        WHEN 1970339280589295618 THEN '豆包'
        WHEN 9003000000000000000 THEN '腾讯混元'
        WHEN 1970339280681570306 THEN '通义千问'
        ELSE name
    END,
    sort = CASE ai_model_config_id
        WHEN 9002500000000000100 THEN 1
        WHEN 2026505489033986050 THEN 2
        WHEN 9005200000000000000 THEN 3
        WHEN 1970687514398605965 THEN 4
        WHEN 1970339280480243713 THEN 5
        WHEN 1970339280287305729 THEN 6
        WHEN 1970339280589295618 THEN 7
        WHEN 9003000000000000000 THEN 8
        WHEN 1970339280681570306 THEN 9
        ELSE sort
    END,
    update_time = NOW()
WHERE ai_model_config_id IN (
    9002500000000000100, 2026505489033986050, 9005200000000000000,
    1970687514398605965, 1970339280480243713, 1970339280287305729,
    1970339280589295618, 9003000000000000000, 1970339280681570306
);

COMMIT;
