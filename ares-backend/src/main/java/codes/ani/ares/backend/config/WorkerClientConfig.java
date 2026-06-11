package codes.ani.ares.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class WorkerClientConfig {
    @Value("${ares.inference-worker.url}")
    private String workerBaseUrl;

    @Bean
    public RestClient inferenceWorkerClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(workerBaseUrl)
                .build();
    }
}
