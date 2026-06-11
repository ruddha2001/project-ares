package codes.ani.ares.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class WorkerClientConfig {
    @Value("${ares.inference-worker.url}")
    private String workerBaseUrl;

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient inferenceWorkerClient(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(180000);
        return restClientBuilder
                .requestFactory(factory)
                .baseUrl(workerBaseUrl)
                .requestInterceptor((request, body, execution) -> {
                    ClientHttpResponse response = execution.execute(request, body);
                    log.info("[INTERCEPT] Request URI: {}, Headers: {}", request.getURI(), request.getHeaders());
                    log.info("[INTERCEPT] Response Status: {}, Headers: {}", response.getStatusCode(), response.getHeaders());
                    return response;
                })
                .build();
    }
}
