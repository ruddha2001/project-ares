package codes.ani.ares.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;

@Configuration
public class RestClientConfig {
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build())).build();
    }
}
