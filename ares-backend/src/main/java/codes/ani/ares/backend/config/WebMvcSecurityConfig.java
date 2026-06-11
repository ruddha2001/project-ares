package codes.ani.ares.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcSecurityConfig implements WebMvcConfigurer {
    private final DeveloperTokenInterceptor developerTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(developerTokenInterceptor).addPathPatterns("/api/v1/baseline/**", "/api/v1/jobs/**", "/api/v1/job/**");
    }
}
