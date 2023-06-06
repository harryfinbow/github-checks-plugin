package io.jenkins.plugins.checks.github;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.util.BuildData;

import io.jenkins.plugins.checks.github.config.GitHubChecksConfig;

/**
 * Provides a {@link GitHubChecksContext} for a Jenkins job that uses a supported {@link GitSCM}.
 */
class GitSCMChecksContext extends GitHubChecksContext {
    private static final int VALID_REPOSITORY_PATH_SEGMENTS = 2;
    private final Run<?, ?> run;
    private final GitHubChecksConfig config;

    /**
     * Creates a {@link GitSCMChecksContext} according to the run. All attributes are computed during this period.
     *
     * @param run    a run of a GitHub Branch Source project
     * @param runURL the URL to the Jenkins run
     */
    GitSCMChecksContext(final Run<?, ?> run, final String runURL, final GitHubChecksConfig config) {
        this(run, runURL, new SCMFacade(), config);
    }

    GitSCMChecksContext(final Run<?, ?> run, final String runURL, final SCMFacade scmFacade, final GitHubChecksConfig config) {
        super(run.getParent(), runURL, scmFacade);

        this.run = run;
        this.config = config;
    }

    @Override
    protected Optional<Run<?, ?>> getRun() {
        return Optional.of(run);
    }

    @Override
    public String getHeadSha() {
        try {
            String commitEnvVar = this.config.getCommitEnvVar();
            if (commitEnvVar.isEmpty()) {
                  commitEnvVar = "GIT_COMMIT";
            }
            String head = getEnvironmentVariable(commitEnvVar);
            if (StringUtils.isNotBlank(head)) {
                return head;
            }
            return getLastBuiltRevisionFromBuildData();
        }
        catch (IOException | InterruptedException e) {
            // ignore and return a default
        }
        return StringUtils.EMPTY;
    }

    public String getEnvironmentVariable(String var) throws IOException, InterruptedException {
        return StringUtils.defaultString(run.getEnvironment(TaskListener.NULL).get(var));
    }

    private String getLastBuiltRevisionFromBuildData() {
        BuildData gitBuildData = run.getAction(BuildData.class);
        if (gitBuildData != null) {
            Revision lastBuiltRevision = gitBuildData.getLastBuiltRevision();
            if (lastBuiltRevision != null) {
                return lastBuiltRevision.getSha1().getName();
            }
        }
        return StringUtils.EMPTY;
    }

    @Override
    public String getRepository() {
        try {
            String repositoryURL = getEnvironmentVariable(this.config.getRepoEnvVar());
            if (repositoryURL.isEmpty()) {
                repositoryURL = getUserRemoteConfig().getUrl();
            }
            if (StringUtils.isNotBlank(repositoryURL)) {
                return getRepository(repositoryURL);
            }
        }
        catch (IOException | InterruptedException e) {
            // ignore and return a default
        }
        return StringUtils.EMPTY;
    }

    @VisibleForTesting
    String getRepository(final String repositoryUrl) {
        if (StringUtils.isBlank(repositoryUrl)) {
            return StringUtils.EMPTY;
        }

        if (repositoryUrl.startsWith("http")) {
            URL url;
            try {
                url = new URL(repositoryUrl);
            }
            catch (MalformedURLException e) {
                return StringUtils.EMPTY;
            }

            String[] pathParts = StringUtils.removeStart(url.getPath(), "/").split("/");
            if (pathParts.length == VALID_REPOSITORY_PATH_SEGMENTS) {
                return pathParts[0] + "/" + StringUtils.removeEnd(pathParts[1], ".git");
            }
        }
        else if (repositoryUrl.matches("git@.+:.+\\/.+")) {
            return StringUtils.removeEnd(repositoryUrl.split(":")[1], ".git");
        }

        return StringUtils.EMPTY;
    }

    @Override
    @CheckForNull
    protected String getCredentialsId() {
        try {
            String credentialsId = this.config.getCredentialsId();
            if (credentialsId.isEmpty()) {
                credentialsId = getUserRemoteConfig().getCredentialsId();
            }
            return credentialsId;
        }
        catch (IOException | InterruptedException e) {
            // ignore and return a default
        }
        return StringUtils.EMPTY;
    }

    private UserRemoteConfig getUserRemoteConfig() {
        return getScmFacade().getUserRemoteConfig(resolveGitSCM());
    }

    private GitSCM resolveGitSCM() {
        Optional<GitSCM> gitSCM = getScmFacade().findGitSCM(run);
        if (gitSCM.isPresent()) {
            return gitSCM.get();
        }
        throw new IllegalStateException(
                "Skipped publishing GitHub checks: no Git SCM source available for job: " + getJob().getName());
    }

    @Override
    public boolean isValid(final FilteredLog logger) {
        logger.logError("Trying to resolve checks parameters from Git SCM...");

        if (!getScmFacade().findGitSCM(run).isPresent()) {
            logger.logError("Job does not use Git SCM");

            return false;
        }

        if (!hasValidCredentials(logger)) {
            logger.logError("Job does not have valid credentials");

            return false;
        }

        String repository = getRepository();
        if (StringUtils.isEmpty(repository)) {
            logger.logError("Repository url is not valid, requiring one of the following schemes:\n"
                    + "\t1. \"git@git-server:repository-owner/repository(.git)\"\n"
                    + "\t2. \"http(s)://git-server/repository-owner/repository(.git)\"");

            return false;
        }

        if (getHeadSha().isEmpty()) {
            logger.logError("No HEAD SHA found for '%s'", repository);

            return false;
        }

        logger.logInfo("Using GitSCM repository '%s' for GitHub checks", repository);

        return true;
    }
}
