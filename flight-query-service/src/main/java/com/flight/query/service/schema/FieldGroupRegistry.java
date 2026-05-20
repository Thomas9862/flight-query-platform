package com.flight.query.service.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 字段分组注册器
 * <p>
 * 维护所有字段组的定义。每个组包含：
 * 1. semanticDescription - 中文语义描述，用于向量化匹配
 * 2. fieldDetail - 详细字段说明，匹配成功后注入到Prompt中
 * <p>
 * 新增字段分组只需在 {@link #buildAllGroups()} 中添加即可，其他代码不需要改动。
 */
public final class FieldGroupRegistry {

    private FieldGroupRegistry() {
    }

    /**
     * 获取所有字段分组
     */
    public static List<FieldGroup> buildAllGroups() {
        List<FieldGroup> groups = new ArrayList<>();

        groups.add(buildTimeGroup());
        groups.add(buildOrderGroup());
        groups.add(buildRouteGroup());
        groups.add(buildProfitGroup());
        groups.add(buildAncillaryGroup());
        groups.add(buildPaymentGroup());
        groups.add(buildPassengerGroup());
        groups.add(buildSupplierGroup());

        return Collections.unmodifiableList(groups);
    }

    // ── 时间维度 ──────────────────────────────────────────────

    private static FieldGroup buildTimeGroup() {
        return new FieldGroup(
                "时间组",
                "时间日期相关查询，包括下单日期、星期几、月份、小时、时间范围、时间趋势、时间段分析",
                "【时间维度字段】\n"
                + "- date_value: DATE类型，下单日期，例如 2025-01-15\n"
                + "- day_of_the_week: 星期几，如 Monday/Tuesday\n"
                + "- month_value: 月份，如 January/February\n"
                + "- hour_value: 下单小时（0-23）\n"
                + "- lead_time: 提前预订天数（下单日到出发日的天数）\n"
                + "注意：时间筛选统一用 date_value 字段"
        );
    }

    // ── 订单基本信息 ──────────────────────────────────────────

    private static FieldGroup buildOrderGroup() {
        return new FieldGroup(
                "订单组",
                "订单基本信息查询，包括订单号、订单状态、品牌、市场、渠道来源、设备类型、用户付款金额",
                "【订单基本信息字段】\n"
                + "- user_order_id: 用户订单号（唯一键）\n"
                + "- user_order_status: 订单状态（PAID=已支付, CANCELLED=已取消, REFUNDED=已退款, PENDING=待支付）\n"
                + "- brand: 品牌（skytours / iwofly / airytrip）\n"
                + "- market: 市场/国家\n"
                + "- meta: 流量来源渠道（如 google / skyscanner / kayak）\n"
                + "- cid_site: CID站点标识\n"
                + "- device: 设备类型（desktop / mobile / tablet）\n"
                + "- customer_id: 客户ID\n"
                + "- customer_country: 客户所在国家\n"
                + "- user_order_price_ttl: 用户订单总价\n"
                + "- user_order_price_ttl_currency: 用户订单总价币种\n"
                + "- user_order_price_ttl_usd: 用户订单总价（USD）\n"
                + "- user_paid_price_ttl: 用户实付金额\n"
                + "- virtual_order: 是否虚拟订单（0=否, 1=是）\n"
                + "注意：统计真实订单时加条件 virtual_order = 0 或 virtual_order IS NULL"
        );
    }

    // ── 航线信息 ──────────────────────────────────────────────

    private static FieldGroup buildRouteGroup() {
        return new FieldGroup(
                "航线组",
                "航线航班相关查询，包括出发到达机场、国家、大洲、航司、航班号、单程往返、舱位等级、飞行时长、航线",
                "【航线信息字段】\n"
                + "- route_type: 单程OW / 往返RT\n"
                + "- route: 航线（如 LHR-PVG）\n"
                + "- outbound_origin_airport / outbound_arrival_airport: 去程出发/到达机场（IATA三字码）\n"
                + "- outbound_origin_country / outbound_arrival_country: 去程出发/到达国家\n"
                + "- outbound_origin_continent / outbound_arrival_continent: 去程出发/到达大洲\n"
                + "- inbound_origin_airport / inbound_arrival_airport: 回程出发/到达机场\n"
                + "- inbound_origin_country / inbound_arrival_country: 回程出发/到达国家\n"
                + "- inbound_origin_continent / inbound_arrival_continent: 回程出发/到达大洲\n"
                + "- outbound_marketing_airline: 去程营销航司\n"
                + "- outbound_validating_airline: 去程出票航司\n"
                + "- outbound_flight_no: 去程航班号\n"
                + "- outbound_flight_duration: 去程飞行时长（分钟）\n"
                + "- inbound_marketing_airline: 回程营销航司\n"
                + "- inbound_flight_no: 回程航班号\n"
                + "- inbound_flight_duration: 回程飞行时长（分钟）\n"
                + "- cabin_class: 舱位等级（Y=经济舱, C=商务舱, F=头等舱）\n"
                + "- segment_quantity: 航段数量\n"
                + "- outbound_time_local: 去程起飞时间（当地时间）\n"
                + "- inbound_time_local: 回程起飞时间（当地时间）\n"
                + "- trip_duration: 行程总时长\n"
                + "注意：查欧洲航线用 outbound_arrival_continent = 'Europe' 或 outbound_origin_continent = 'Europe'"
        );
    }

    // ── 收入与利润 ───────────────────────────────────────────

    private static FieldGroup buildProfitGroup() {
        return new FieldGroup(
                "利润组",
                "利润收入成本相关查询，包括机票利润、订单利润、加价金额、总收入、总成本、毛利",
                "【收入与利润字段（优先使用USD字段）】\n"
                + "- flight_profit_usd: 机票利润（USD）\n"
                + "- order_estimated_profit_usd: 订单预估总利润（USD）\n"
                + "- total_ancillary_profit_usd: 增值产品总利润（USD）\n"
                + "- total_policy_markup_usd: 总策略加价（USD）\n"
                + "- total_cost_usd: 总成本（USD）\n"
                + "- markup_flight_amt_usd: 机票加价金额（USD）\n"
                + "- markup_flight_total_usd: 机票加价总额（USD）\n"
                + "- flight_supplier_ttl_pay_due: 应付供应商金额\n"
                + "- flight_order_total_usd: 机票订单总价（USD）\n"
                + "- flight_order_fare_total_usd: 机票票面价合计（USD）\n"
                + "- flight_order_tax_total_usd: 机票税费合计（USD）\n"
                + "- user_order_price_ttl_usd: 用户订单总价（USD）\n"
                + "- meta_cpa_commission_usd: Meta CPA佣金（USD）\n"
                + "- markup_auto_price_usd: 自动加价金额（USD）\n"
                + "- markup_price_change_usd: 价格变动金额（USD）\n"
                + "- supplier_mark_up: 供应商加价\n"
                + "注意：分析利润时统一使用 _usd 结尾字段，保证币种一致"
        );
    }

    // ── 增值产品 ──────────────────────────────────────────────

    private static FieldGroup buildAncillaryGroup() {
        return new FieldGroup(
                "增值产品组",
                "增值产品查询，包括行李、选座、值机、保险、AirHelp、BRB、Koala、服务包、CFAR、eSIM、VLG等附加产品的销售价、利润和供应商结算价",
                "【增值产品字段（USD）】\n"
                + "- baggage_profit_usd: 行李利润\n"
                + "- baggage_ttl_profit_usd: 行李总利润\n"
                + "- baggage_provider: 行李供应商\n"
                + "- baggage_supplier_settlement_price_usd: 行李供应商结算价\n"
                + "- seat_profit_usd: 选座利润\n"
                + "- seat_provider: 选座供应商\n"
                + "- markup_seat_ttl_price_usd: 选座加价总额\n"
                + "- checkin_profit_usd: 值机利润\n"
                + "- markup_checkin_ttl_amt_usd: 值机加价总额\n"
                + "- air_help_profit_usd: AirHelp保险利润\n"
                + "- air_help_price_usd: AirHelp价格\n"
                + "- koala_profit_usd: Koala产品利润\n"
                + "- koala_price_usd: Koala价格\n"
                + "- brb_profit_usd: BRB产品利润\n"
                + "- rp_profit_usd: RP产品利润\n"
                + "- service_package_ttl_usd: 服务包总额\n"
                + "- service_package_type: 服务包类型\n"
                + "- bundle_profit_usd: 捆绑包利润\n"
                + "- bundle_type: 捆绑包类型\n"
                + "- cfar_self_owned_profit_usd: CFAR自营利润\n"
                + "- cim_profit_usd: CIM利润\n"
                + "- vlg_profit_usd: VLG利润\n"
                + "- e_sim_profit_usd: eSIM利润\n"
                + "- fdc_profit_usd: FDC利润\n"
                + "- product_package: 产品包名称\n"
                + "- group_code: 组合代码\n"
                + "注意：增值利润汇总用 total_ancillary_profit_usd 字段"
        );
    }

    // ── 支付信息 ──────────────────────────────────────────────

    private static FieldGroup buildPaymentGroup() {
        return new FieldGroup(
                "支付组",
                "支付相关查询，包括支付方式、支付网关、支付手续费、支付尝试次数、代金券",
                "【支付信息字段】\n"
                + "- payment_gateway: 支付网关（如 Checkout / Adyen / Worldpay）\n"
                + "- payment_method: 支付方式（如 CreditCard / PayPal / Alipay）\n"
                + "- payment_fee: 支付手续费\n"
                + "- payment_trial_count: 支付尝试次数\n"
                + "- markup_payment_cost_amt_usd: 支付成本加价（USD）\n"
                + "- voucher_used_usd: 使用的代金券金额（USD）"
        );
    }

    // ── 乘客信息 ──────────────────────────────────────────────

    private static FieldGroup buildPassengerGroup() {
        return new FieldGroup(
                "乘客组",
                "乘客相关查询，包括乘客总数、成人数、儿童数、婴儿数、人均价格",
                "【乘客信息字段】\n"
                + "- total_passengers: 总乘客数\n"
                + "- adt_quantity: 成人数量\n"
                + "- chd_quantity: 儿童数量\n"
                + "- inf_quantity: 婴儿数量\n"
                + "- flight_adult_total_usd: 成人机票总价（USD）\n"
                + "- flight_child_total_usd: 儿童机票总价（USD）\n"
                + "- flight_infant_total_usd: 婴儿机票总价（USD）\n"
                + "- flight_adult_fare_usd: 成人票面价（USD）\n"
                + "- flight_adult_tax_usd: 成人税费（USD）"
        );
    }

    // ── 供应商信息 ────────────────────────────────────────────

    private static FieldGroup buildSupplierGroup() {
        return new FieldGroup(
                "供应商组",
                "供应商相关查询，包括机票供应商名称、供应商订单号、PCC代码、供应商应付金额",
                "【供应商信息字段】\n"
                + "- flight_supplier: 机票供应商名称\n"
                + "- flight_supplier_booking_no: 供应商预订号\n"
                + "- flight_pcc_code: 供应商PCC代码\n"
                + "- flight_order: 机票订单号\n"
                + "- flight_order_status: 机票订单状态\n"
                + "- flight_supplier_ttl_pay_due: 供应商应付金额\n"
                + "- flight_supplier_total_pay_due_currency: 供应商应付币种\n"
                + "- ancillary_total_supplier_settlement_price_usd: 增值产品供应商结算总价（USD）"
        );
    }
}
