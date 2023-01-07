package org.openmbee.ve.confluence.resource;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/environments/")
public class EnvironmentResource {

    private static final String BASE_NAMESPACE = "org.openmbee.ve.ve-confluence-app";
    private static final String ENVIRONMENT_LIST_KEY = BASE_NAMESPACE + ".environment-list";
    private static final String DEFAULT_ENVIRONMENT_ID = "default";

    @ComponentImport
    private final UserManager userManager;
    @ComponentImport
    private final PluginSettingsFactory pluginSettingsFactory;
    @ComponentImport
    private final TransactionTemplate transactionTemplate;

    @Inject
    public EnvironmentResource(
            UserManager userManager, PluginSettingsFactory pluginSettingsFactory,
            TransactionTemplate transactionTemplate) {
        this.userManager = userManager;
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.transactionTemplate = transactionTemplate;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@Context HttpServletRequest request) {
        UserKey userKey = userManager.getRemoteUserKey(request);
        if (userKey == null || !userManager.isSystemAdmin(userKey)) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        List<Environment> environments = transactionTemplate.execute(() -> {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            return ids(settings)
                    .map(id -> {
                        Environment environment = get(id, settings);
                        if (environment == null) {
                            environment = new Environment();
                            environment.setId(id);
                        }
                        return environment;
                    })
                    .collect(Collectors.toList());
        });
        return Response.ok(environments).build();
    }

    private Stream<String> ids(PluginSettings settings) {
        String environmentListValue = (String) settings.get(ENVIRONMENT_LIST_KEY);
        if (environmentListValue == null || environmentListValue.isEmpty()) {
            return Stream.empty();
        }
        return Arrays.stream(environmentListValue.split("\\."))
                .filter(Objects::nonNull);
    }

    @GET
    @Path("{environmentId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@PathParam("environmentId") String environmentId, @Context HttpServletRequest request) {
        UserKey userKey = userManager.getRemoteUserKey(request);
        if (userKey == null || !userManager.isSystemAdmin(userKey)) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        if (isIdDisallowed(environmentId)) {
            return Response.status(Status.BAD_REQUEST).entity(buildError("Identifier contains disallowed characters.")).build();
        }
        Environment environment = transactionTemplate.execute(() ->
                get(environmentId, pluginSettingsFactory.createGlobalSettings()));
        return environment != null ? Response.ok(environment).build() : Response.status(Status.NOT_FOUND).build();
    }

    public static @Nullable Environment get(String environmentId, PluginSettings settings) {
        String viewerJsUri = (String) settings.get(BASE_NAMESPACE + ".environment." + environmentId + ".viewer-uri");
        if (viewerJsUri == null) {
            return null;
        }

        String spaceAllowlist = (String) settings.get(BASE_NAMESPACE + ".environment." + environmentId + ".space-allowlist");
        if (spaceAllowlist == null) {
            return null;
        }

        Environment result = new Environment();
        result.setId(environmentId);
        result.setViewerUri(viewerJsUri);
        result.setSpaceAllowlist(spaceAllowlist);
        return result;
    }

    public static @Nullable Environment getDefault(PluginSettings settings) {
        return get(DEFAULT_ENVIRONMENT_ID, settings);
    }

    @DELETE
    @Path("{environmentId}")
    public Response delete(@PathParam("environmentId") String environmentId, @Context HttpServletRequest request) {
        UserKey userKey = userManager.getRemoteUserKey(request);
        if (userKey == null || !userManager.isSystemAdmin(userKey)) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        if (isIdDisallowed(environmentId)) {
            return Response.status(Status.BAD_REQUEST).entity(buildError("Identifier contains disallowed characters.")).build();
        }
        transactionTemplate.execute(() -> {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            List<String> ids = ids(settings).collect(Collectors.toList());
            boolean removed = ids.remove(environmentId);
            if (removed) {
                settings.remove(BASE_NAMESPACE + ".environment." + environmentId + ".viewer-uri");
                settings.remove(BASE_NAMESPACE + ".environment." + environmentId + ".space-allowlist");
                environments(ids, settings);
            }
            return null;
        });
        return Response.noContent().build();
    }

    @PUT
    @Path("{environmentId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(
            @PathParam("environmentId") String environmentId,
            Environment environment,
            @Context HttpServletRequest request) {
        UserKey userKey = userManager.getRemoteUserKey(request);
        if (userKey == null || !userManager.isSystemAdmin(userKey)) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        if (isIdDisallowed(environmentId)) {
            return Response.status(Status.BAD_REQUEST).entity(buildError("Identifier contains disallowed characters.")).build();
        }
        if (environment == null) {
            return Response.status(Status.BAD_REQUEST).entity(buildError("Request body must be provided.")).build();
        }
        if (environment.getId() != null && !environment.getId().equals(environmentId)) {
            return Response.status(Status.BAD_REQUEST).entity(buildError("Identifiers in URI and request body do not match.")).build();
        }
        String viewerUri = environment.getViewerUri();
        if (viewerUri == null || viewerUri.isEmpty()) {
            return Response.status(Status.BAD_REQUEST).entity(buildError("Viewer URI must be provided.")).build();
        }
        String _spaceAllowlist = environment.getSpaceAllowlist();
        String spaceAllowlist = _spaceAllowlist != null && !_spaceAllowlist.isEmpty() ? _spaceAllowlist : "";

        transactionTemplate.execute(() -> {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            String environmentListValue = (String) settings.get(ENVIRONMENT_LIST_KEY);
            List<String> environmentList;
            if (environmentListValue != null && !environmentListValue.isEmpty()) {
                String[] split = environmentListValue.split("\\.");
                environmentList = new ArrayList<>(split.length + 1);
                environmentList.addAll(Arrays.asList(split));
            } else {
                environmentList = new ArrayList<>(1);
            }
            if (!environmentList.contains(environmentId)) {
                environmentList.add(environmentId);
                environments(environmentList, settings);
            }
            settings.put(BASE_NAMESPACE + ".environment." + environmentId + ".viewer-uri", viewerUri);
            settings.put(BASE_NAMESPACE + ".environment." + environmentId + ".space-allowlist", spaceAllowlist);
            return null;
        });

        environment.setId(environmentId);
        return Response.ok(environment).build();
    }

    private void environments(List<String> environments, PluginSettings settings) {
        settings.put(ENVIRONMENT_LIST_KEY, String.join(".", environments));
    }

    private boolean isIdDisallowed(String id) {
        return id.isEmpty() || id.indexOf('.') != -1;
    }

    private Error buildError(String message) {
        Error error = new Error();
        error.setMessage(message);
        return error;
    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Environment {
        @XmlElement
        private String id;
        @XmlElement
        private String viewerUri;
        @XmlElement
        private String spaceAllowlist;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getViewerUri() {
            return viewerUri;
        }

        public void setViewerUri(String viewerUri) {
            this.viewerUri = viewerUri;
        }

        public String getSpaceAllowlist() {
            return spaceAllowlist;
        }

        public void setSpaceAllowlist(String spaceAllowlist) {
            this.spaceAllowlist = spaceAllowlist;
        }
    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    static final class Error {
        @XmlElement(name = "error")
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
