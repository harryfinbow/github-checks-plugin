package io.jenkins.plugins.checks.github.config;

/**
 * Project-level configurations for users to customize GitHub checks.
 */
public interface GitHubChecksConfig {
    /**
     * Defines whether to output verbose console log.
     *
     * @return true for verbose log
     */
    boolean isVerboseConsoleLog();

    /**
     * Gets an environment variable from which to read the commit sha
     *
     * @return string where empty value signals to use the default env var
     */
    String getCommitEnvVar();

    /**
     * Gets an environment variable from which to read the repository url
     *
     * @return string where empty value signals to read from the User Remote Config
     */
    String getRepoEnvVar();
}
