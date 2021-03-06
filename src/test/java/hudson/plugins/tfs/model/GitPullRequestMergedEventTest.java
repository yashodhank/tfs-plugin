package hudson.plugins.tfs.model;

import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.net.URI;

/**
 * A class to test {@link GitPullRequestMergedEvent}.
 */
public class GitPullRequestMergedEventTest {

    @Test
    public void decodeGitPullRequestMerged() throws Exception {
        final GitPullRequestMergedEvent.Factory factory = new GitPullRequestMergedEvent.Factory();
        final String inputString = factory.getSampleRequestPayload();
        final JSONObject input = JSONObject.fromObject(inputString);

        final PullRequestMergeCommitCreatedEventArgs actual = GitPullRequestMergedEvent.decodeGitPullRequestMerged(input);

        Assert.assertEquals(URI.create("https://fabrikam.visualstudio.com/"), actual.collectionUri);
        Assert.assertEquals(URI.create("https://fabrikam.visualstudio.com/_git/Fabrikam"), actual.repoUri);
        Assert.assertEquals("Fabrikam", actual.projectId);
        Assert.assertEquals("Fabrikam", actual.repoId);
        Assert.assertEquals("eef717f69257a6333f221566c1c987dc94cc0d72", actual.commit);
        Assert.assertEquals("Jamal Hartnett", actual.pushedBy);
        Assert.assertEquals(1, actual.pullRequestId);
        Assert.assertEquals(-1, actual.iterationId);
    }
}
