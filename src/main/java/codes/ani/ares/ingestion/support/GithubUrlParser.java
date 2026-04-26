package codes.ani.ares.ingestion.support;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses supported GitHub URLs into a structured reference containing owner,
 * repository, and optional pull request number.
 */
@Component
public class GithubUrlParser {

    /**
     * Matches GitHub pull request URLs such as {@code https://github.com/{owner}/{repo}/pull/{number}}.
     */
    private static final Pattern PR_PATTERN =
            Pattern.compile("^https://github\\.com/(?<owner>[^/]+)/(?<repo>[^/]+)/pull/(?<prNumber>\\d+)(?:/.*)?$", Pattern.CASE_INSENSITIVE);
    /**
     * Matches GitHub repository URLs such as {@code https://github.com/{owner}/{repo}}.
     */
    private static final Pattern REPO_PATTERN =
            Pattern.compile("^https://github\\.com/(?<owner>[^/]+)/(?<repo>[^/]+)(?:/.*)?$", Pattern.CASE_INSENSITIVE);

    /**
     * Parsed GitHub URL components.
     *
     * @param owner    GitHub repository owner or organization
     * @param repo     repository name
     * @param prNumber pull request number when URL targets a pull request; otherwise {@code null}
     */
    public record GithubRef(String owner, String repo, Long prNumber) {
        /**
         * Indicates whether this reference points to a pull request.
         *
         * @return {@code true} when {@code prNumber} is present, otherwise {@code false}
         */
        public boolean isPullRequest() {
            return prNumber != null;
        }
    }

    /**
     * Parses a GitHub repository or pull request URL.
     *
     * @param uri absolute GitHub URL
     * @return parsed {@link GithubRef}
     * @throws IllegalArgumentException when the URI does not match supported GitHub URL formats
     */
    public GithubRef parse(String uri) {
        Matcher prMatcher = PR_PATTERN.matcher(uri);
        if (prMatcher.matches()) {
            try {
                return new GithubRef(
                        prMatcher.group("owner"),
                        prMatcher.group("repo"),
                        Long.parseLong(prMatcher.group("prNumber"))
                );
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid pull request number in URI: " + uri, e);
            }
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
