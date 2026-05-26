-- ============================================================
-- 机票订单自然语言查询平台 - 初始化SQL
-- ============================================================

-- 查询任务记录表
CREATE TABLE IF NOT EXISTS `query_task` (
    `id`              BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`      VARCHAR(128)  NOT NULL                COMMENT '会话ID（userId_终端UUID）',
    `user_question`   VARCHAR(500)  NOT NULL                COMMENT '用户原始问题',
    `generated_sql`   TEXT                                  COMMENT '模型生成的SQL',
    `result_json`     MEDIUMTEXT                            COMMENT '查询结果JSON',
    `conclusion`      TEXT                                  COMMENT '自然语言分析结论',
    `status`          TINYINT(4)    NOT NULL DEFAULT 0      COMMENT '状态：0=处理中 1=成功 2=SQL生成失败 3=SQL执行失败 4=安全拦截',
    `retry_count`     INT(11)       NOT NULL DEFAULT 0      COMMENT 'SQL生成重试次数',
    `matched_groups`  VARCHAR(256)                          COMMENT '匹配的字段组名称（逗号分隔）',
    `cost_ms`         BIGINT(20)                            COMMENT '耗时（毫秒）',
    `error_msg`       VARCHAR(1000)                         COMMENT '错误信息',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='自然语言查询任务记录表';


-- ============================================================
-- 业务知识库表（RAG 知识源，配合 Elasticsearch 向量检索）
-- ============================================================
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id`          BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `category`    VARCHAR(50)   NOT NULL                COMMENT '知识分类：BUSINESS_GLOSSARY/FARE_RULES/AIRLINE_KNOWLEDGE/QUERY_GUIDE',
    `title`       VARCHAR(200)  NOT NULL                COMMENT '知识标题',
    `content`     TEXT          NOT NULL                COMMENT '知识内容',
    `status`      TINYINT(4)    NOT NULL DEFAULT 1      COMMENT '状态：1=启用 0=禁用',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='RAG业务知识库';


-- ============================================================
-- 知识库初始化数据（从原 .txt 文件迁移）
-- ============================================================

-- ── 业务指标定义（BUSINESS_GLOSSARY）──────────────────────────
INSERT INTO `knowledge_base` (`category`, `title`, `content`) VALUES
('BUSINESS_GLOSSARY', '虚拟订单 virtual_order', 'virtual_order 字段用于标记测试订单和虚拟订单。virtual_order=0 或 NULL 表示真实订单，virtual_order=1 表示虚拟/测试订单。在统计真实业务数据时，必须过滤虚拟订单，条件为：virtual_order = 0 OR virtual_order IS NULL。'),
('BUSINESS_GLOSSARY', '提前预订天数 lead_time', 'lead_time 是指用户下单日期到出发日期之间的天数差。例如用户1月1日下单，1月15日出发，则lead_time=14。该指标用于分析预订行为模式和定价策略效果。lead_time越大通常票价越低。'),
('BUSINESS_GLOSSARY', '订单利润 order_estimated_profit_usd', 'order_estimated_profit_usd 是订单的预估总利润（USD），包含机票利润（flight_profit_usd）和增值产品总利润（total_ancillary_profit_usd）。这是衡量订单盈利能力的核心指标。'),
('BUSINESS_GLOSSARY', '机票利润 flight_profit_usd', 'flight_profit_usd 是机票部分的利润（USD），等于用户支付的机票金额减去供应商成本和各项费用。是机票业务的核心利润指标。'),
('BUSINESS_GLOSSARY', '增值产品总利润 total_ancillary_profit_usd', 'total_ancillary_profit_usd 是所有增值产品利润的汇总（USD），包括行李、选座、值机、保险等附加产品。增值产品是OTA利润的重要组成部分。'),
('BUSINESS_GLOSSARY', '策略加价 total_policy_markup_usd', 'total_policy_markup_usd 是基于定价策略自动加价的总金额（USD）。加价策略可能基于航线热度、预订时间、市场竞争等因素动态调整。'),
('BUSINESS_GLOSSARY', 'Meta CPA佣金 meta_cpa_commission_usd', 'meta_cpa_commission_usd 是支付给流量渠道（如Google Flights, Skyscanner, Kayak等）的CPA推广佣金（USD）。需要在利润分析中扣除此成本。'),
('BUSINESS_GLOSSARY', 'PCC代码 flight_pcc_code', 'PCC（Pseudo City Code）是GDS系统中的代理人代码标识。不同PCC可能对应不同的供应商协议和采购价格。PCC分析有助于优化供应商成本。'),
('BUSINESS_GLOSSARY', '舱位等级 cabin_class', 'cabin_class 用字母标识：Y=经济舱（Economy）, C=商务舱（Business）, F=头等舱（First）。不同舱位等级的利润率和客单价差异很大。'),
('BUSINESS_GLOSSARY', '品牌 brand', 'brand 字段标识不同的产品品牌线，如 skytours、iwofly、airytrip。不同品牌面向不同的市场和客户群体，定价策略也不同。'),
('BUSINESS_GLOSSARY', '市场 market', 'market 字段标识目标销售市场/国家。同一航线在不同市场的定价和利润可能有显著差异。'),
('BUSINESS_GLOSSARY', '流量来源 meta', 'meta 字段记录用户的流量来源渠道，如 google（Google Flights）、skyscanner、kayak、direct（直接访问）等。用于分析各渠道的获客效率和ROI。');

-- ── 退改签与业务规则（FARE_RULES）──────────────────────────
INSERT INTO `knowledge_base` (`category`, `title`, `content`) VALUES
('FARE_RULES', '机票退改签规则概述', '机票退改签规则由航空公司制定，不同舱位、不同票价规则差异很大。一般来说：经济舱特价票通常不可退不可改，或需要支付较高手续费；经济舱全价票通常可退可改，手续费较低；商务舱和头等舱通常退改政策较为灵活。退改签产生的费用变化会体现在 flight_profit_usd 和订单状态（user_order_status）中。'),
('FARE_RULES', '订单状态流转', 'PENDING（待支付）：用户已创建订单但尚未完成支付。PAID（已支付）：用户已完成支付，订单生效。CANCELLED（已取消）：订单被取消，可能是用户主动取消或超时未支付。REFUNDED（已退款）：已完成退款流程。分析有效订单时通常筛选 user_order_status = ''PAID''。'),
('FARE_RULES', '航线类型说明', 'OW（One Way）：单程，只有去程航班。RT（Round Trip）：往返，包含去程和回程。分析航线收入时注意 RT 订单包含两段行程，客单价通常高于 OW。'),
('FARE_RULES', '增值产品体系', 'OTA平台通过增值产品提升客单利润，主要产品包括：行李额（Baggage）利润字段 baggage_profit_usd；选座（Seat）利润字段 seat_profit_usd；值机（Check-in）利润字段 checkin_profit_usd；航班保险（AirHelp）利润字段 air_help_profit_usd；服务包（Service Package）字段 service_package_ttl_usd；捆绑包（Bundle）利润字段 bundle_profit_usd；CFAR（Cancel For Any Reason）任意原因免费取消保险，利润字段 cfar_self_owned_profit_usd。'),
('FARE_RULES', '支付方式分析要点', '不同支付方式的手续费率不同，影响实际利润：信用卡（CreditCard）手续费率通常1.5%-3%；PayPal手续费率通常2.9%+固定费用；本地支付方式因地区而异。支付手续费字段：payment_fee，支付成本加价字段：markup_payment_cost_amt_usd。'),
('FARE_RULES', 'GDS与供应商说明', '机票供应商（flight_supplier）是票源渠道，可能是GDS（如Amadeus, Sabre, Travelport）或直连航司API。不同供应商的采购成本和出票能力不同，PCC代码（flight_pcc_code）标识不同的供应商接入点。');

-- ── 航司知识（AIRLINE_KNOWLEDGE）──────────────────────────
INSERT INTO `knowledge_base` (`category`, `title`, `content`) VALUES
('AIRLINE_KNOWLEDGE', '三大航空联盟', '星空联盟（Star Alliance）：中国国航CA、汉莎LH、美联航UA、全日空NH、新加坡航空SQ、土耳其航空TK、加拿大航空AC、泰航TG、韩亚航空OZ、深圳航空ZH等。天合联盟（SkyTeam）：东方航空MU、南方航空CZ、法航AF、荷航KL、达美DL、大韩航空KE、越南航空VN、沙特航空SV等。寰宇一家（oneworld）：国泰航空CX、英航BA、美航AA、日航JL、澳航QF、伊比利亚IB、芬兰航空AY、马来西亚航空MH等。联盟内航司通常有代码共享航班、联运协议和常旅客互认，分析航线竞争时应考虑联盟因素。'),
('AIRLINE_KNOWLEDGE', '主要航司运力特点', '阿联酋航空EK：迪拜枢纽，覆盖全球六大洲，主力宽体机队。卡塔尔航空QR：多哈枢纽，五星航空，欧亚非中转。土耳其航空TK：伊斯坦布尔枢纽，航线网络最广的航司之一。新加坡航空SQ：高端服务标杆，东南亚枢纽。国泰航空CX：香港枢纽，亚洲长途航线。'),
('AIRLINE_KNOWLEDGE', '廉价航空识别', '常见廉价航空（LCC）：欧洲：Ryanair FR、easyJet U2、Wizz Air W6、Vueling VY、Norwegian DY。亚洲：AirAsia AK/D7、Spring Airlines 9C、Peach MM、Scoot TR。北美：Spirit NK、Frontier F9。廉价航空的特点：基础票价低但增值产品收入比例高，行李选座等附加服务单独收费。分析时注意区分全服务航空和廉价航空的利润结构差异。'),
('AIRLINE_KNOWLEDGE', '热门国际航线市场', '中欧航线：中国出发至欧洲主要城市，竞争激烈，价格敏感。中东中转：EK/QR/TK 提供有竞争力的中转价格。东南亚航线：短途高频，廉航占比大。跨大西洋：欧洲至北美，传统高收益航线。');

-- ── 查询分析指南（QUERY_GUIDE）──────────────────────────
INSERT INTO `knowledge_base` (`category`, `title`, `content`) VALUES
('QUERY_GUIDE', '利润分析常见维度', '按航司分析：GROUP BY outbound_marketing_airline，对比各航司的平均利润和订单量。按航线分析：GROUP BY route，找出高利润和低利润航线。按时间趋势：GROUP BY date_value 或 month_value，观察利润变化趋势。按品牌分析：GROUP BY brand，对比不同品牌的盈利能力。按市场分析：GROUP BY market，分析各市场的利润贡献。按渠道分析：GROUP BY meta，评估各流量渠道的ROI。'),
('QUERY_GUIDE', '常用聚合指标', '总利润：SUM(order_estimated_profit_usd)。平均客单利润：AVG(order_estimated_profit_usd)。订单量：COUNT(DISTINCT user_order_id)。平均客单价：AVG(user_order_price_ttl_usd)。增值产品渗透率：可用 SUM(CASE WHEN total_ancillary_profit_usd > 0 THEN 1 ELSE 0 END) / COUNT(*) 计算。'),
('QUERY_GUIDE', '分析注意事项', '始终过滤虚拟订单：WHERE (virtual_order = 0 OR virtual_order IS NULL)。金额分析统一使用USD字段，避免多币种混淆。利润分析建议筛选已支付订单：user_order_status = ''PAID''。大数据量查询建议加 LIMIT 限制，避免返回过多数据。按日期范围筛选使用 date_value BETWEEN ''YYYY-MM-DD'' AND ''YYYY-MM-DD''。'),
('QUERY_GUIDE', '异常数据排查思路', '利润异常高/低：检查是否存在退款、加价异常、汇率问题。订单量骤降：检查特定日期范围、品牌或市场筛选条件。查询结果为空：放宽筛选条件，逐步排查是时间、航司还是市场条件导致。');


-- ============================================================
-- Schema 字段组表（动态管理，替代硬编码的 FieldGroupRegistry）
-- ============================================================
CREATE TABLE IF NOT EXISTS `schema_field_group` (
    `id`            BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `table_name`    VARCHAR(100)  NOT NULL DEFAULT 'report_reservation_real_time' COMMENT '所属表名（预留多表扩展）',
    `group_name`    VARCHAR(50)   NOT NULL COMMENT '字段组名称，如"利润组"',
    `semantic_desc` VARCHAR(500)  NOT NULL COMMENT '中文语义描述（用于向量匹配）',
    `field_detail`  TEXT          NOT NULL COMMENT '字段详细说明（匹配后注入Prompt）',
    `status`        TINYINT(4)    NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    `sort_order`    INT(11)       NOT NULL DEFAULT 0 COMMENT '排序序号',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_table_name` (`table_name`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Schema字段组（动态管理）';


-- ============================================================
-- Schema 字段组初始数据（从 FieldGroupRegistry 迁移）
-- ============================================================
INSERT INTO `schema_field_group` (`table_name`, `group_name`, `semantic_desc`, `field_detail`, `sort_order`) VALUES
('report_reservation_real_time', '时间组',
 '时间日期相关查询，包括下单日期、星期几、月份、小时、时间范围、时间趋势、时间段分析',
 '【时间维度字段】\n- date_value: DATE类型，下单日期，例如 2025-01-15\n- day_of_the_week: 星期几，如 Monday/Tuesday\n- month_value: 月份，如 January/February\n- hour_value: 下单小时（0-23）\n- lead_time: 提前预订天数（下单日到出发日的天数）\n注意：时间筛选统一用 date_value 字段',
 1),

('report_reservation_real_time', '订单组',
 '订单基本信息查询，包括订单号、订单状态、品牌、市场、渠道来源、设备类型、用户付款金额',
 '【订单基本信息字段】\n- user_order_id: 用户订单号（唯一键）\n- user_order_status: 订单状态（PAID=已支付, CANCELLED=已取消, REFUNDED=已退款, PENDING=待支付）\n- brand: 品牌（skytours / iwofly / airytrip）\n- market: 市场/国家\n- meta: 流量来源渠道（如 google / skyscanner / kayak）\n- cid_site: CID站点标识\n- device: 设备类型（desktop / mobile / tablet）\n- customer_id: 客户ID\n- customer_country: 客户所在国家\n- user_order_price_ttl: 用户订单总价\n- user_order_price_ttl_currency: 用户订单总价币种\n- user_order_price_ttl_usd: 用户订单总价（USD）\n- user_paid_price_ttl: 用户实付金额\n- virtual_order: 是否虚拟订单（0=否, 1=是）\n注意：统计真实订单时加条件 virtual_order = 0 或 virtual_order IS NULL',
 2),

('report_reservation_real_time', '航线组',
 '航线航班相关查询，包括出发到达机场、国家、大洲、航司、航班号、单程往返、舱位等级、飞行时长、航线',
 '【航线信息字段】\n- route_type: 单程OW / 往返RT\n- route: 航线（如 LHR-PVG）\n- outbound_origin_airport / outbound_arrival_airport: 去程出发/到达机场（IATA三字码）\n- outbound_origin_country / outbound_arrival_country: 去程出发/到达国家\n- outbound_origin_continent / outbound_arrival_continent: 去程出发/到达大洲\n- inbound_origin_airport / inbound_arrival_airport: 回程出发/到达机场\n- inbound_origin_country / inbound_arrival_country: 回程出发/到达国家\n- inbound_origin_continent / inbound_arrival_continent: 回程出发/到达大洲\n- outbound_marketing_airline: 去程营销航司\n- outbound_validating_airline: 去程出票航司\n- outbound_flight_no: 去程航班号\n- outbound_flight_duration: 去程飞行时长（分钟）\n- inbound_marketing_airline: 回程营销航司\n- inbound_flight_no: 回程航班号\n- inbound_flight_duration: 回程飞行时长（分钟）\n- cabin_class: 舱位等级（Y=经济舱, C=商务舱, F=头等舱）\n- segment_quantity: 航段数量\n- outbound_time_local: 去程起飞时间（当地时间）\n- inbound_time_local: 回程起飞时间（当地时间）\n- trip_duration: 行程总时长\n注意：查欧洲航线用 outbound_arrival_continent = ''Europe'' 或 outbound_origin_continent = ''Europe''',
 3),

('report_reservation_real_time', '利润组',
 '利润收入成本相关查询，包括机票利润、订单利润、加价金额、总收入、总成本、毛利',
 '【收入与利润字段（优先使用USD字段）】\n- flight_profit_usd: 机票利润（USD）\n- order_estimated_profit_usd: 订单预估总利润（USD）\n- total_ancillary_profit_usd: 增值产品总利润（USD）\n- total_policy_markup_usd: 总策略加价（USD）\n- total_cost_usd: 总成本（USD）\n- markup_flight_amt_usd: 机票加价金额（USD）\n- markup_flight_total_usd: 机票加价总额（USD）\n- flight_supplier_ttl_pay_due: 应付供应商金额\n- flight_order_total_usd: 机票订单总价（USD）\n- flight_order_fare_total_usd: 机票票面价合计（USD）\n- flight_order_tax_total_usd: 机票税费合计（USD）\n- user_order_price_ttl_usd: 用户订单总价（USD）\n- meta_cpa_commission_usd: Meta CPA佣金（USD）\n- markup_auto_price_usd: 自动加价金额（USD）\n- markup_price_change_usd: 价格变动金额（USD）\n- supplier_mark_up: 供应商加价\n注意：分析利润时统一使用 _usd 结尾字段，保证币种一致',
 4),

('report_reservation_real_time', '增值产品组',
 '增值产品查询，包括行李、选座、值机、保险、AirHelp、BRB、Koala、服务包、CFAR、eSIM、VLG等附加产品的销售价、利润和供应商结算价',
 '【增值产品字段（USD）】\n- baggage_profit_usd: 行李利润\n- baggage_ttl_profit_usd: 行李总利润\n- baggage_provider: 行李供应商\n- baggage_supplier_settlement_price_usd: 行李供应商结算价\n- seat_profit_usd: 选座利润\n- seat_provider: 选座供应商\n- markup_seat_ttl_price_usd: 选座加价总额\n- checkin_profit_usd: 值机利润\n- markup_checkin_ttl_amt_usd: 值机加价总额\n- air_help_profit_usd: AirHelp保险利润\n- air_help_price_usd: AirHelp价格\n- koala_profit_usd: Koala产品利润\n- koala_price_usd: Koala价格\n- brb_profit_usd: BRB产品利润\n- rp_profit_usd: RP产品利润\n- service_package_ttl_usd: 服务包总额\n- service_package_type: 服务包类型\n- bundle_profit_usd: 捆绑包利润\n- bundle_type: 捆绑包类型\n- cfar_self_owned_profit_usd: CFAR自营利润\n- cim_profit_usd: CIM利润\n- vlg_profit_usd: VLG利润\n- e_sim_profit_usd: eSIM利润\n- fdc_profit_usd: FDC利润\n- product_package: 产品包名称\n- group_code: 组合代码\n注意：增值利润汇总用 total_ancillary_profit_usd 字段',
 5),

('report_reservation_real_time', '支付组',
 '支付相关查询，包括支付方式、支付网关、支付手续费、支付尝试次数、代金券',
 '【支付信息字段】\n- payment_gateway: 支付网关（如 Checkout / Adyen / Worldpay）\n- payment_method: 支付方式（如 CreditCard / PayPal / Alipay）\n- payment_fee: 支付手续费\n- payment_trial_count: 支付尝试次数\n- markup_payment_cost_amt_usd: 支付成本加价（USD）\n- voucher_used_usd: 使用的代金券金额（USD）',
 6),

('report_reservation_real_time', '乘客组',
 '乘客相关查询，包括乘客总数、成人数、儿童数、婴儿数、人均价格',
 '【乘客信息字段】\n- total_passengers: 总乘客数\n- adt_quantity: 成人数量\n- chd_quantity: 儿童数量\n- inf_quantity: 婴儿数量\n- flight_adult_total_usd: 成人机票总价（USD）\n- flight_child_total_usd: 儿童机票总价（USD）\n- flight_infant_total_usd: 婴儿机票总价（USD）\n- flight_adult_fare_usd: 成人票面价（USD）\n- flight_adult_tax_usd: 成人税费（USD）',
 7),

('report_reservation_real_time', '供应商组',
 '供应商相关查询，包括机票供应商名称、供应商订单号、PCC代码、供应商应付金额',
 '【供应商信息字段】\n- flight_supplier: 机票供应商名称\n- flight_supplier_booking_no: 供应商预订号\n- flight_pcc_code: 供应商PCC代码\n- flight_order: 机票订单号\n- flight_order_status: 机票订单状态\n- flight_supplier_ttl_pay_due: 供应商应付金额\n- flight_supplier_total_pay_due_currency: 供应商应付币种\n- ancillary_total_supplier_settlement_price_usd: 增值产品供应商结算总价（USD）',
 8);
