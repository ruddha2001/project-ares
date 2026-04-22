package codes.ani.ares.ingestion.support;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GithubUrlParser {

    private static final Pattern PR_PATTERN =
            Pattern.compile("^https://github\\.com/(?<owner>[^/]+)/(?<repo>[^/]+)/pull/(?<prNumber>\\d+)/?$");

    public record GitHubPrRef(String owner, String repo, long prNumber) {
    }

    public GitHubPrRef parse(String uri) {
        Matcher matcher = PR_PATTERN.matcher(uri);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid GitHub PR URL: " + uri);
        }

        return new GitHubPrRef(
                matcher.group("owner"),
                matcher.group("repo"),
                Long.parseLong(matcher.group("prNumber"))
        );
    }
}
