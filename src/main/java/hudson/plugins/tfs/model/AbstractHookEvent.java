package hudson.plugins.tfs.model;

import net.sf.json.JSONObject;

public abstract class AbstractHookEvent {

    private JSONObject response;

    public interface Factory {
        AbstractHookEvent create();
        String getSampleRequestPayload();
    }

    public JSONObject getResponse() {
        return response;
    }

    /**
     * Actually do the work of the hook event, using the supplied
     * {@code requestPayload} and returning the output as a {@link JSONObject}.
     *
     * @param requestPayload a {@link JSONObject} representing the hook event's input
     *
     * @return a {@link JSONObject} representing the hook event's output
     */
    public abstract JSONObject perform(final JSONObject requestPayload);

}
