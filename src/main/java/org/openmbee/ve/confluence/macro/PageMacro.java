package org.openmbee.ve.confluence.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.openmbee.ve.confluence.resource.EnvironmentResource;
import org.openmbee.ve.confluence.resource.EnvironmentResource.Environment;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Map;

public class PageMacro implements Macro {

    private static final String ERROR_TITLE = "View Editor Page Error";

    @ComponentImport
    private final PluginSettingsFactory pluginSettingsFactory;
    @ComponentImport
    private final TransactionTemplate transactionTemplate;

    @Inject
    public PageMacro(PluginSettingsFactory pluginSettingsFactory, TransactionTemplate transactionTemplate) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public String execute(Map<String, String> params, String body, ConversionContext conversionContext) {
        Pair<String, Environment> entry = transactionTemplate.execute(() -> {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            String environmentId = params.get("environment");
            Environment environment = environmentId != null ? EnvironmentResource.get(environmentId, settings) :
                    EnvironmentResource.getDefault(settings);
            return new ImmutablePair<>(environmentId, environment);
        });
        String environmentId = entry.getKey();
        Environment environment = entry.getValue();

        if (environment == null) {
            if (environmentId != null) {
                return buildErrorElement(String.format(
                        "An unknown View Editor environment has been specified: \"%s\". Please edit the macro and provide a valid environment.",
                        environmentId));
            }
            else {
                return buildErrorElement(
                        "A View Editor environment was not specified and a default environment has not been configured. Please edit the macro and provide an environment.");
            }
        }

        if (environmentId == null) {
            environmentId = "default";
        }

        String spaceAllowlist = environment.getSpaceAllowlist();
        if (spaceAllowlist != null && !spaceAllowlist.isEmpty()) {
            String spaceKey = conversionContext.getSpaceKey();
            if (Arrays.stream(spaceAllowlist.split(","))
                    .noneMatch(spaceKey::equalsIgnoreCase)) {
                return buildErrorElement(String.format(
                        "The provided View Editor environment \"%s\" is not allowed for the current Confluence space \"%s\". Please edit the macro and provide an allowed environment.",
                        environmentId, spaceKey));
            }
        }

        String viewerJsUri = environment.getViewerUri();
        if (viewerJsUri == null || viewerJsUri.isEmpty()) {
            return buildErrorElement(String.format(
                    "The provided View Editor environment has not been configured properly: \"%s\".",
                    environmentId));
        }
        return String.format("<script type=\"text/javascript\" src=\"%s\"></script>", viewerJsUri);
    }

    @Override
    public BodyType getBodyType() {
        return BodyType.NONE;
    }

    @Override
    public OutputType getOutputType() {
        return OutputType.BLOCK;
    }

    private String buildErrorElement(String message) {
        return String.format("<div class=\"aui-message aui-message-error\"><p class=\"title\"><strong>%s</strong></p><p>%s</p></div>", ERROR_TITLE, message);
    }
}
