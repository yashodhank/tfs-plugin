package hudson.plugins.tfs.model;

import net.sf.ezmorph.MorpherRegistry;
import net.sf.json.JSONObject;
import net.sf.json.util.JSONTokener;
import net.sf.json.util.JSONUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * A class to test {@link VstsGitStatus}.
 */
public class VstsGitStatusTest {

    @Test public void toJson_typical() {
        final VstsGitStatus cut = new VstsGitStatus();
        cut.state = GitStatusState.Pending;
        cut.description = "The build is in progress";
        cut.targetUrl = "https://ci.fabrikam.com/my-project/build/124";
        cut.context = new GitStatusContext("Build124", "continuous-integration");

        final String actual = cut.toJson();

        final String expected =
            "{" +
                "\"state\":\"Pending\"," +
                "\"description\":\"The build is in progress\"," +
                "\"targetUrl\":\"https://ci.fabrikam.com/my-project/build/124\"," +
                "\"context\":" +
                "{" +
                    "\"name\":\"Build124\"," +
                    "\"genre\":\"continuous-integration\"" +
                "}" +
            "}";
        Assert.assertEquals(expected, actual);
    }

    @Test public void fromJsonString_vsts() throws Exception {
        final String input =
            "{" +
                "\"state\":\"succeeded\"," +
                "\"description\":\"SUCCESS\"," +
                "\"targetUrl\":\"https://ci.fabrikam.com/my-project/build/124\"," +
                "\"context\":" +
                "{" +
                    "\"name\":\"Build124\"," +
                    "\"genre\":\"continuous-integration\"" +
                "}" +
            "}";

        final JSONTokener tokener = new JSONTokener(input);
        final JSONObject jsonObject = JSONObject.fromObject(tokener);
        final MorpherRegistry registry = JSONUtils.getMorpherRegistry();
        registry.registerMorpher(GitStatusStateMorpher.INSTANCE);
        final VstsGitStatus actual;
        try {
            actual = (VstsGitStatus) jsonObject.toBean(VstsGitStatus.class);
        }
        finally {
            registry.deregisterMorpher(GitStatusStateMorpher.INSTANCE);
        }
        Assert.assertEquals(GitStatusState.Succeeded, actual.state);
    }
}
