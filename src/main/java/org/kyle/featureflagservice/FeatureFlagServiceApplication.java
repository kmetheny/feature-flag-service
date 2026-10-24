package org.kyle.featureflagservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FeatureFlagServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(FeatureFlagServiceApplication.class, args);
    }

}
