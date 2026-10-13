package study.yume.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class PlanLiveUpdateConfig {
    @Bean
    @ConditionalOnProperty(prefix = "spring.jpa", name = "open-in-view", havingValue = "true", matchIfMissing = true)
    OpenEntityManagerInViewInterceptor openEntityManagerInViewInterceptor(EntityManagerFactory entityManagerFactory) {
        OpenEntityManagerInViewInterceptor interceptor = new OpenEntityManagerInViewInterceptor();
        interceptor.setEntityManagerFactory(entityManagerFactory);
        return interceptor;
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.jpa", name = "open-in-view", havingValue = "true", matchIfMissing = true)
    WebMvcConfigurer openEntityManagerInViewInterceptorConfigurer(OpenEntityManagerInViewInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                // A streaming response must not retain the request's EntityManager/DB connection.
                registry.addWebRequestInterceptor(interceptor).excludePathPatterns(
                        "/api/plans/*/events", "/api/public/plans/share/events", "/api/goshuin/**");
            }
        };
    }

    @Bean
    ThreadPoolTaskScheduler planLiveUpdateHeartbeatScheduler() {
        return scheduler("plan-sse-heartbeat-");
    }

    @Bean
    ThreadPoolTaskScheduler planLiveUpdateAuthorizationScheduler() {
        return scheduler("plan-sse-authorization-");
    }

    private ThreadPoolTaskScheduler scheduler(String threadNamePrefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
