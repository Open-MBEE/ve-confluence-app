package org.openmbee.ve.confluence.macro;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;

import java.util.Map;

public class TableMacro implements Macro {

    @Override
    public String execute(Map<String, String> params, String body, ConversionContext conversionContext) {
        return "<div><div class=\"ve-table-init\"></div></div>";
    }

    @Override
    public BodyType getBodyType() { return BodyType.NONE; }

    @Override
    public OutputType getOutputType() { return OutputType.BLOCK; }
}
