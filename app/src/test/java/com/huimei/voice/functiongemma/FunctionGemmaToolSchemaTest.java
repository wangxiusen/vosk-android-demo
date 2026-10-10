package com.huimei.voice.functiongemma;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class FunctionGemmaToolSchemaTest {
    @Test
    public void exposesOnlyTheThreeWhitelistedTools() throws Exception {
        JSONArray tools = new JSONArray(FunctionGemmaToolSchema.jsonDefinitions());

        assertEquals(3, tools.length());
        Set<String> names = new HashSet<>();
        for (int index = 0; index < tools.length(); index++) {
            JSONObject tool = tools.getJSONObject(index);
            names.add(tool.getString("name"));
        }
        assertEquals(new HashSet<>(Arrays.asList(
                "power_on", "power_off", "set_gas_duration")), names);
    }

    @Test
    public void powerToolsHaveNoArgumentsAndDurationUsesTheFixedIntegerEnum() throws Exception {
        JSONArray tools = new JSONArray(FunctionGemmaToolSchema.jsonDefinitions());

        for (int index = 0; index < tools.length(); index++) {
            JSONObject tool = tools.getJSONObject(index);
            JSONObject parameters = tool.getJSONObject("parameters");
            if (!"set_gas_duration".equals(tool.getString("name"))) {
                assertEquals(0, parameters.getJSONObject("properties").length());
                assertEquals(0, parameters.getJSONArray("required").length());
                continue;
            }

            assertEquals(1, parameters.getJSONObject("properties").length());
            JSONObject minutes = parameters.getJSONObject("properties")
                    .getJSONObject("duration_minutes");
            assertEquals("integer", minutes.getString("type"));
            assertEquals("[30,60,120,480]", minutes.getJSONArray("enum").toString());
            assertEquals(
                    "duration_minutes",
                    parameters.getJSONArray("required").getString(0));
            assertFalse(parameters.optBoolean("additionalProperties", true));
        }
    }
}
