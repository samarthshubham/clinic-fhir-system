package io.github.samarthshubham.clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClinicApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(ClinicApiApplication.class, args);
  }
}
