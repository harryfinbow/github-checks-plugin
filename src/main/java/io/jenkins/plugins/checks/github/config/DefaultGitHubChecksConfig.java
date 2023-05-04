package io.jenkins.plugins.checks.github.config;

/**
 * Default implementation for {@link GitHubChecksConfig}.
 */
public class DefaultGitHubChecksConfig implements GitHubChecksConfig {
    @Override
    public boolean isVerboseConsoleLog() {
        return false;
    }

    @Override
    public String getCommit() {
        return "";
    }

    @Override
    public String getRepository() {
        return "";
    }
}
