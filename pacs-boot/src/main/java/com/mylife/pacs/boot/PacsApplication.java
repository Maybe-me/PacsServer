package com.mylife.pacs.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.mylife.pacs")
public class PacsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PacsApplication.class, args);
    }
}
