package codes.ani.ares.backend.config;

import codes.ani.ares.backend.model.User;
import codes.ani.ares.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class DeveloperTokenInterceptor implements HandlerInterceptor {
    private final UserRepository userRepository;
    private final RestClient githubClient = RestClient.builder().baseUrl("https://api.github.com").build();

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object object) throws Exception {
        String ghPatHeader = request.getHeader("X-ARES-GH-PAT");

        if (ghPatHeader == null || ghPatHeader.isBlank()) {
            return rejectRequest(response, "Missing mandatory X-ARES-GH-PAT verification credentials.");
        }

        try {
            Map<String, Object> githubResponse = githubClient.get()
                    .uri("/user")
                    .header("Authorization", "Bearer " + ghPatHeader)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (githubResponse == null || !githubResponse.containsKey("login")) {
                return rejectRequest(response, "GitHub authentication handshake failed.");
            }

            String githubUsername = (String) githubResponse.get("login");
            log.info("[AUTH] Verified identity for GitHub user: {}", githubUsername);

            User user = userRepository.findByGithubUsernameIgnoreCase(githubUsername)
                    .orElse(null);

            if (user == null) {
                return rejectRequest(response, "User '" + githubUsername + "' is not registered within this cluster.");
            }

            if (request.getRequestURI().startsWith("/api/v1/baseline/addUser") && !user.isAdmin()) {
                log.warn("🛑 [RBAC VIOLATION] Non-admin user '{}' tried hitting administrative endpoints.", githubUsername);
                return rejectRequest(response, "Forbidden. Administrator clearance required to execute this operation.");
            }

            request.setAttribute("CURRENT_ARES_USER", user);
            return true;

        } catch (Exception e) {
            log.error("❌ Security context resolution failure: {}", e.getMessage());
            return rejectRequest(response, "Security gateway failed to resolve access tokens.");
        }
    }
    private boolean rejectRequest(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"error\": \"%s\"}", message));
        return false;
    }
}
