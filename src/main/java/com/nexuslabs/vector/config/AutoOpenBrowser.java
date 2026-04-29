package com.nexuslabs.vector.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

@Component
public class AutoOpenBrowser implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(AutoOpenBrowser.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        int port = event.getApplicationContext().getEnvironment()
                .getProperty("server.port", int.class, 8080);
        
        log.info("Opening V.E.C.T.O.R at http://localhost:{}", port);
        
        try {
            Thread.sleep(1500);
            Desktop.getDesktop().browse(new URI("http://localhost:" + port));
            log.info("Browser opened successfully");
        } catch (Exception e) {
            log.info("Could not open browser automatically. Open http://localhost:{} manually", port);
        }
    }
}