package codes.ani.ares.ingestion.support;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GithubUrlParser {

    private static final Pattern PR_PATTERN =
            Pattern.compile("^https://github\\.com/(?<owner>[^/]+)/(?<repo>[^/]+)/pull/(?<prNumber>\\d+)/?$");
    private static final Pattern REPO_PATTERN =
            Pattern.compile("^https://github\\.com/(?<owner>[^/]+)/(?<repo>[^/]+)/?$");

    public record GithubRef(String owner, String repo, Long prNumber) {
        public boolean isPullRequest() {
            return prNumber != null;
        }
    }

    public GithubRef parse(String uri) {
        Matcher prMatcher = PR_PATTERN.matcher(uri);
        if (prMatcher.matches()) {
            return new GithubRef(
                    prMatcher.group("owner"),
                    prMatcher.group("repo"),
                    Long.parseLong(prMatcher.group("prNumber"))
            );
        }

        Matcher repoMatcher = REPO_PATTERN.matcher(uri);
        if (repoMatcher.matches()) {
            return new GithubRef(
                    repoMatcher.group("owner"),
                    repoMatcher.group("repo"),
                    null
            );
        }

        throw new IllegalArgumentException("Unsupported GitHub URI format: " + uri);
    }
}
