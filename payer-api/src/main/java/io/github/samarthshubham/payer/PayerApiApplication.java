package io.github.samarthshubham.payer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class PayerApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(PayerApiApplication.class, args);
  }
}
