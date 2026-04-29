package com.nexuslabs.vector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VectorApplication {
    public static void main(String[] args) {
        for (String arg : args) {
            if (arg.equals("--app") || arg.equals("-a")) {
                com.nexuslabs.vector.desktop.VectorApp.main(args);
                return;
            }
        }
        SpringApplication.run(VectorApplication.class, args);
    }
}