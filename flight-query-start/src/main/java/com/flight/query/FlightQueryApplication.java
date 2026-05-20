package com.flight.query;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 机票订单自然语言查询平台 - 启动入口
 *
 * @author wangjinbao
 */
@SpringBootApplication(scanBasePackages = "com.flight.query")
@MapperScan("com.flight.query.domain.mapper")
public class FlightQueryApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightQueryApplication.class, args);
    }
}
