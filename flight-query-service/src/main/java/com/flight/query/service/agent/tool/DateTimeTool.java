package com.flight.query.service.agent.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

@Slf4j
@Component
public class DateTimeTool {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Tool("获取当前日期和时间信息。用于处理用户问题中的相对时间引用，如'上周''本月''最近7天''去年'等。返回当前日期以及常用时间范围的起止日期。")
    public String getCurrentDateTime() {
        LocalDate today = LocalDate.now();

        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate lastWeekStart = weekStart.minusWeeks(1);
        LocalDate lastWeekEnd = weekStart.minusDays(1);

        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = monthStart.minusMonths(1);
        LocalDate lastMonthEnd = monthStart.minusDays(1);

        LocalDate quarterStart = today.with(today.getMonth().firstMonthOfQuarter())
                .with(TemporalAdjusters.firstDayOfMonth());
        LocalDate yearStart = today.withDayOfYear(1);
        LocalDate lastYearStart = yearStart.minusYears(1);
        LocalDate lastYearEnd = yearStart.minusDays(1);

        String result = "当前日期信息：\n"
                + "- 今天: " + today.format(FMT) + " (" + today.getDayOfWeek() + ")\n"
                + "- 本周: " + weekStart.format(FMT) + " ~ " + today.format(FMT) + "\n"
                + "- 上周: " + lastWeekStart.format(FMT) + " ~ " + lastWeekEnd.format(FMT) + "\n"
                + "- 本月: " + monthStart.format(FMT) + " ~ " + today.format(FMT) + "\n"
                + "- 上月: " + lastMonthStart.format(FMT) + " ~ " + lastMonthEnd.format(FMT) + "\n"
                + "- 本季度: " + quarterStart.format(FMT) + " ~ " + today.format(FMT) + "\n"
                + "- 今年: " + yearStart.format(FMT) + " ~ " + today.format(FMT) + "\n"
                + "- 去年: " + lastYearStart.format(FMT) + " ~ " + lastYearEnd.format(FMT) + "\n"
                + "- 最近7天: " + today.minusDays(6).format(FMT) + " ~ " + today.format(FMT) + "\n"
                + "- 最近30天: " + today.minusDays(29).format(FMT) + " ~ " + today.format(FMT) + "\n"
                + "\n在SQL中使用 date_value 字段进行时间筛选，格式为 'yyyy-MM-dd'。";

        log.info("[DateTimeTool] 返回当前日期信息, today={}", today);
        return result;
    }
}
