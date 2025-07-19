package com.realtime.events.realtimeEvents.config;

import lombok.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableAsync
public class WebMvcAsyncConfig implements WebMvcConfigurer {

    private final AsyncTaskExecutor mvcTaskExecutor;

    public WebMvcAsyncConfig(AsyncTaskExecutor mvcTaskExecutor) {
        this.mvcTaskExecutor = mvcTaskExecutor;
    }

    @Override
    public void configureAsyncSupport(@NonNull AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcTaskExecutor);
        configurer.setDefaultTimeout(30000); // Optional: default timeout in ms
    }
}
