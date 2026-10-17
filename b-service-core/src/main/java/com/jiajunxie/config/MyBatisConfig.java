package com.jiajunxie.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.jiajunxie.mapper")
public class MyBatisConfig {}
