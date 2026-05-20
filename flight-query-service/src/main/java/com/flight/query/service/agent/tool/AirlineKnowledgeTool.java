package com.flight.query.service.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AirlineKnowledgeTool {

    private static final Map<String, String> IATA_TO_NAME = new LinkedHashMap<>();

    static {
        // 中国航司
        IATA_TO_NAME.put("CA", "Air China 中国国航");
        IATA_TO_NAME.put("MU", "China Eastern 东方航空");
        IATA_TO_NAME.put("CZ", "China Southern 南方航空");
        IATA_TO_NAME.put("HU", "Hainan Airlines 海南航空");
        IATA_TO_NAME.put("3U", "Sichuan Airlines 四川航空");
        IATA_TO_NAME.put("ZH", "Shenzhen Airlines 深圳航空");
        IATA_TO_NAME.put("MF", "Xiamen Air 厦门航空");
        // 欧洲航司
        IATA_TO_NAME.put("LH", "Lufthansa 汉莎航空");
        IATA_TO_NAME.put("BA", "British Airways 英国航空");
        IATA_TO_NAME.put("AF", "Air France 法国航空");
        IATA_TO_NAME.put("KL", "KLM 荷兰皇家航空");
        IATA_TO_NAME.put("LX", "Swiss International 瑞士航空");
        IATA_TO_NAME.put("OS", "Austrian Airlines 奥地利航空");
        IATA_TO_NAME.put("AY", "Finnair 芬兰航空");
        IATA_TO_NAME.put("SK", "SAS 北欧航空");
        IATA_TO_NAME.put("IB", "Iberia 伊比利亚航空");
        IATA_TO_NAME.put("TK", "Turkish Airlines 土耳其航空");
        IATA_TO_NAME.put("SU", "Aeroflot 俄罗斯航空");
        IATA_TO_NAME.put("FR", "Ryanair 瑞安航空");
        IATA_TO_NAME.put("U2", "easyJet 易捷航空");
        IATA_TO_NAME.put("W6", "Wizz Air 威兹航空");
        // 中东航司
        IATA_TO_NAME.put("EK", "Emirates 阿联酋航空");
        IATA_TO_NAME.put("QR", "Qatar Airways 卡塔尔航空");
        IATA_TO_NAME.put("EY", "Etihad Airways 阿提哈德航空");
        IATA_TO_NAME.put("SV", "Saudia 沙特航空");
        // 北美航司
        IATA_TO_NAME.put("AA", "American Airlines 美国航空");
        IATA_TO_NAME.put("UA", "United Airlines 美联航");
        IATA_TO_NAME.put("DL", "Delta Air Lines 达美航空");
        IATA_TO_NAME.put("WN", "Southwest Airlines 西南航空");
        IATA_TO_NAME.put("AC", "Air Canada 加拿大航空");
        // 亚太航司
        IATA_TO_NAME.put("SQ", "Singapore Airlines 新加坡航空");
        IATA_TO_NAME.put("CX", "Cathay Pacific 国泰航空");
        IATA_TO_NAME.put("NH", "ANA 全日空");
        IATA_TO_NAME.put("JL", "Japan Airlines 日本航空");
        IATA_TO_NAME.put("KE", "Korean Air 大韩航空");
        IATA_TO_NAME.put("OZ", "Asiana Airlines 韩亚航空");
        IATA_TO_NAME.put("TG", "Thai Airways 泰国航空");
        IATA_TO_NAME.put("VN", "Vietnam Airlines 越南航空");
        IATA_TO_NAME.put("GA", "Garuda Indonesia 印尼鹰航");
        IATA_TO_NAME.put("MH", "Malaysia Airlines 马来西亚航空");
        IATA_TO_NAME.put("QF", "Qantas 澳洲航空");
        IATA_TO_NAME.put("AI", "Air India 印度航空");
        // 非洲航司
        IATA_TO_NAME.put("ET", "Ethiopian Airlines 埃塞俄比亚航空");
        IATA_TO_NAME.put("MS", "EgyptAir 埃及航空");
        // 南美航司
        IATA_TO_NAME.put("LA", "LATAM Airlines 拉美航空");
        IATA_TO_NAME.put("AV", "Avianca 哥伦比亚航空");
    }

    @Tool("查询航空公司IATA代码和名称的映射关系。用于将用户提到的航空公司名称转换为数据库中使用的IATA两字码，或反向查询航司名称。数据库中的航司字段存储的是IATA代码。")
    public String lookupAirline(
            @P("航空公司名称（中文或英文）或IATA两字码") String query) {

        log.info("[AirlineKnowledgeTool] 查询航司: {}", query);

        String upperQuery = query.trim().toUpperCase();

        // 精确匹配 IATA 代码
        if (IATA_TO_NAME.containsKey(upperQuery)) {
            return "IATA代码 " + upperQuery + " 对应航司: " + IATA_TO_NAME.get(upperQuery)
                    + "\n在SQL中筛选该航司: outbound_marketing_airline = '" + upperQuery + "'";
        }

        // 模糊匹配名称
        String lowerQuery = query.trim().toLowerCase();
        String matches = IATA_TO_NAME.entrySet().stream()
                .filter(e -> e.getValue().toLowerCase().contains(lowerQuery))
                .map(e -> e.getKey() + " - " + e.getValue())
                .collect(Collectors.joining("\n"));

        if (!matches.isEmpty()) {
            return "匹配到以下航司：\n" + matches
                    + "\n在SQL中使用IATA代码筛选，如: outbound_marketing_airline = '代码'";
        }

        return "未找到匹配的航司。常见航司代码示例：CA=国航, MU=东航, CZ=南航, EK=阿联酋, BA=英航, LH=汉莎。"
                + "\n数据库中航司字段使用IATA两字码存储。";
    }
}
