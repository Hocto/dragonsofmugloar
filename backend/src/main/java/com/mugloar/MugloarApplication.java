package com.mugloar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MugloarApplication {

    public static void main(String[] args) {
        SpringApplication.run(MugloarApplication.class, args);
    }
}
