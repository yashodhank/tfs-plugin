package hudson.plugins.tfs;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.BuildAuthorizationToken;
import hudson.model.Job;
import hudson.model.UnprotectedRootAction;
import hudson.plugins.tfs.model.AbstractCommand;
import hudson.plugins.tfs.model.BuildCommand;
import hudson.plugins.tfs.model.BuildWithParametersCommand;
import hudson.plugins.tfs.model.PingCommand;
import hudson.plugins.tfs.util.EndpointHelper;
import hudson.plugins.tfs.util.MediaType;
import jenkins.model.Jenkins;
import jenkins.util.TimeDuration;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.kohsuke.stapler.ForwardToView;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

/**
 * The endpoint that TFS/Team Services will PUT to when it wants to schedule a build in Jenkins.
 */
@Extension
public class TeamBuildEndpoint implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(TeamBuildEndpoint.class.getName());
    private static final Map<String, AbstractCommand.Factory> COMMAND_FACTORIES_BY_NAME;
    public static final String URL_NAME = "team-build";
    public static final String TEAM_EVENT = "team-event";
    public static final String TEAM_PARAMETERS = "team-parameters";
    public static final String TEAM_BUILD = "team-build";
    public static final String PARAMETER = "parameter";
    static final String URL_PREFIX = "/" + URL_NAME + "/";
    private static final String JSON = "json";

    static {
        final Map<String, AbstractCommand.Factory> map = new TreeMap<String, AbstractCommand.Factory>(String.CASE_INSENSITIVE_ORDER);
        map.put("ping", new PingCommand.Factory());
        map.put("build", new BuildCommand.Factory());
        map.put("buildWithParameters", new BuildWithParametersCommand.Factory());
        COMMAND_FACTORIES_BY_NAME = Collections.unmodifiableMap(map);
    }

    private String commandName;
    private String jobName;

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return URL_NAME;
    }

    public String getCommandName() {
        return commandName;
    }

    public String getJobName() {
        return jobName;
    }

    boolean decodeCommandAndJobNames(final String pathInfo) {
        if (pathInfo.startsWith(URL_PREFIX)) {
            final String restOfPath = pathInfo.substring(URL_PREFIX.length());
            final int firstSlash = restOfPath.indexOf('/');
            if (firstSlash != -1) {
                commandName = restOfPath.substring(0, firstSlash);
                if (firstSlash < restOfPath.length() - 1) {
                    final String encodedJobName = restOfPath.substring(firstSlash + 1);
                    try {
                        jobName = URLDecoder.decode(encodedJobName, MediaType.UTF_8.name());
                    }
                    catch (final UnsupportedEncodingException e) {
                        throw new Error(e);
                    }
                    return true;
                }
            }
            else {
                commandName = restOfPath;
            }
        }

        return false;
    }

    public HttpResponse doIndex(final HttpServletRequest request) throws IOException {
        final Class<? extends TeamBuildEndpoint> me = this.getClass();
        final InputStream stream = me.getResourceAsStream("TeamBuildEndpoint.html");
        final Jenkins instance = Jenkins.getInstance();
        final String rootUrl = instance.getRootUrl();
        final String commandRows = describeCommands(COMMAND_FACTORIES_BY_NAME, URL_NAME);
        try {
            final String template = IOUtils.toString(stream, MediaType.UTF_8);
            final String content = String.format(template, URL_NAME, commandRows, rootUrl);
            return HttpResponses.html(content);
        }
        finally {
            IOUtils.closeQuietly(stream);
        }
    }
    static String describeCommands(final Map<String, AbstractCommand.Factory> commandMap, final String urlName) {
        final String newLine = System.getProperty("line.separator");
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, AbstractCommand.Factory> commandPair : commandMap.entrySet()) {
            final String commandName = commandPair.getKey();
            final AbstractCommand.Factory factory = commandPair.getValue();
            sb.append("<tr>").append(newLine);
            sb.append("<td valign='top'>").append(commandName).append("</td>").append(newLine);
            sb.append("<td valign='top'>").append('/').append(urlName).append('/').append(commandName).append('/').append("JOB_NAME").append("</td>").append(newLine);
            final String rawSample = factory.getSampleRequestPayload();
            final String escapedSample = StringEscapeUtils.escapeHtml4(rawSample);
            sb.append("<td><pre>").append(escapedSample).append("</pre></td>").append(newLine);
            sb.append("</tr>").append(newLine);
        }
        return sb.toString();
    }


    @SuppressWarnings("deprecation" /* We want to do exactly what Jenkins does */)
    void checkPermission(final AbstractProject project, final StaplerRequest req, final StaplerResponse rsp) throws IOException {
        Job<?, ?> job = project;
        final BuildAuthorizationToken authToken = project.getAuthToken();
        hudson.model.BuildAuthorizationToken.checkPermission(job, authToken, req, rsp);
    }

    void dispatch(final StaplerRequest req, final StaplerResponse rsp, final TimeDuration delay) throws IOException {
        try {
            final JSONObject response = innerDispatch(req, rsp, delay);

            if (response.containsKey("created")) {
                rsp.setStatus(SC_CREATED);
            }
            else {
                rsp.setStatus(SC_OK);
            }
            rsp.setContentType(MediaType.APPLICATION_JSON_UTF_8);
            final PrintWriter w = rsp.getWriter();
            final String responseJsonString = response.toString();
            w.print(responseJsonString);
            w.println();
        }
        catch (final IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "IllegalArgumentException", e);
            EndpointHelper.error(SC_BAD_REQUEST, e);
        }
        catch (final ForwardToView e) {
            throw e;
        }
        catch (final Exception e) {
            final String template = "Error while performing reaction to '%s' command.";
            final String message = String.format(template, commandName);
            LOGGER.log(Level.SEVERE, message, e);
            EndpointHelper.error(SC_INTERNAL_SERVER_ERROR, e);
        }
    }

    private JSONObject innerDispatch(final StaplerRequest req, final StaplerResponse rsp, final TimeDuration delay) throws IOException, ServletException {
        commandName = null;
        jobName = null;
        final String pathInfo = req.getPathInfo();
        if (!decodeCommandAndJobNames(pathInfo)) {
            if (commandName == null) {
                throw new IllegalArgumentException("Command not provided");
            }
            if (jobName == null) {
                throw new IllegalArgumentException("Job name not provided after command");
            }
        }

        if (!COMMAND_FACTORIES_BY_NAME.containsKey(commandName)) {
            throw new IllegalArgumentException("Command not implemented");
        }

        final Jenkins jenkins = Jenkins.getInstance();
        final AbstractProject project = jenkins.getItemByFullName(jobName, AbstractProject.class);
        if (project == null) {
            throw new IllegalArgumentException("Project not found");
        }
        checkPermission(project, req, rsp);
        final TimeDuration actualDelay =
                delay == null ? new TimeDuration(project.getQuietPeriod()) : delay;

        final AbstractCommand.Factory factory = COMMAND_FACTORIES_BY_NAME.get(commandName);
        final AbstractCommand command = factory.create();
        final JSONObject response;
        if (isStructuredForm(req.getParameter(JSON))) {
            final JSONObject formData = req.getSubmittedForm();
            response = command.perform(project, req, formData, actualDelay);
        }
        else {
            response = command.perform(project, req, actualDelay);
        }
        return response;
    }

    static boolean isStructuredForm(final String jsonParameter) {
        if (jsonParameter != null) {
            try {
                final JSONObject jsonObject = JSONObject.fromObject(jsonParameter);
                if (jsonObject.containsKey(TEAM_PARAMETERS) || jsonObject.containsKey(TEAM_BUILD) || jsonObject.containsKey(TEAM_EVENT)) {
                    return true;
                }
            }
            catch (final JSONException e) {
                return false;
            }
        }
        return false;
    }

    public void doPing(
            final StaplerRequest request,
            final StaplerResponse response,
            @QueryParameter final TimeDuration delay
    ) throws IOException {
        dispatch(request, response, delay);
    }

    public void doBuild(
            final StaplerRequest request,
            final StaplerResponse response,
            @QueryParameter final TimeDuration delay
    ) throws IOException {
        dispatch(request, response, delay);
    }

    public void doBuildWithParameters(
            final StaplerRequest request,
            final StaplerResponse response,
            @QueryParameter final TimeDuration delay
    ) throws IOException {
        dispatch(request, response, delay);
    }

}
