package com.jiajunxie.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.jiajunxie")
@EntityScan(basePackages = "com.jiajunxie.entity")
@EnableScheduling
public class BillApplication {

  public static void main(String[] args) {
    SpringApplication.run(BillApplication.class, args);
  }

}
