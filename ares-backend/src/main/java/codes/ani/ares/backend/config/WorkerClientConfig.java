package codes.ani.ares.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WorkerClientConfig {
    @Value("${ares.inference-worker.url}")
    private String workerBaseUrl;

    @Bean
    public RestClient inferenceWorkerClient(){
        return RestClient.builder()
                .baseUrl(workerBaseUrl)
                .build();
    }
}
