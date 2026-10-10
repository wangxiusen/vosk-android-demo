package com.huimei.voice.functiongemma;

import com.google.ai.edge.litertlm.OpenApiTool;
import com.google.ai.edge.litertlm.ToolKt;
import com.google.ai.edge.litertlm.ToolProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class FunctionGemmaToolSchema {
    private static final List<String> DEFINITIONS = Collections.unmodifiableList(Arrays.asList(
            "{\"name\":\"power_on\",\"description\":\"Turn on the oxygen concentrator. \u5f00\u542f\u5236\u6c27\u673a。\","
                    + "\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[],"
                    + "\"additionalProperties\":false}}",
            "{\"name\":\"power_off\",\"description\":\"Turn off the oxygen concentrator. \u5173\u95ed\u5236\u6c27\u673a。\","
                    + "\"parameters\":{\"type\":\"object\",\"properties\":{},\"required\":[],"
                    + "\"additionalProperties\":false}}",
            "{\"name\":\"set_gas_duration\",\"description\":\"Set gas production duration in minutes. "
                    + "\u8bbe\u7f6e\u5236\u6c27\u65f6\u957f（\u5206\u949f）。\",\"parameters\":{\"type\":\"object\","
                    + "\"properties\":{\"minutes\":{\"type\":\"integer\",\"enum\":[30,60,120,480]}},"
                    + "\"required\":[\"minutes\"],\"additionalProperties\":false}}"));

    private FunctionGemmaToolSchema() {
    }

    public static String jsonDefinitions() {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < DEFINITIONS.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(DEFINITIONS.get(index));
        }
        return json.append(']').toString();
    }

    static List<ToolProvider> toolProviders() {
        List<ToolProvider> providers = new ArrayList<>(DEFINITIONS.size());
        for (String definition : DEFINITIONS) {
            providers.add(ToolKt.tool(new SchemaOnlyTool(definition)));
        }
        return Collections.unmodifiableList(providers);
    }

    private static final class SchemaOnlyTool implements OpenApiTool {
        private final String definition;

        SchemaOnlyTool(String definition) {
            this.definition = definition;
        }

        @Override
        public String getToolDescriptionJsonString() {
            return definition;
        }

        @Override
        public String execute(String paramsJsonString) {
            throw new UnsupportedOperationException("Automatic tool execution is disabled");
        }
    }
}
