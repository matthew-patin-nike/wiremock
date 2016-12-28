package com.nike.cpspub;

import com.github.tomakehurst.wiremock.extension.*;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

import java.io.IOException;

/**
 * Created by mpatin on 12/27/16.
 */
public class DataCleaner extends ResponseTransformer {

    @Override
    public Response transform(Request request, Response response, FileSource files, Parameters parameters) {

        Map<String, String> keysToClean = (Map<String, String>) parameters.get("keysToClean");
        String cleaned = ""; //we'd rather return nothing than unclean json.
        if (keysToClean.size() == 0) {
            throw new RuntimeException("Transformer missing keysToClean, add them to options.json");
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode responseJson = (JsonNode) mapper.readTree(response.getBody());
            cleanAllKeys(responseJson, keysToClean);
            cleaned = responseJson.toString();
        } catch (IOException io) {
           io.printStackTrace();
        }

        return Response.Builder.like(response)
                .but().body(cleaned)
                .build();
    }

    private void cleanAllKeys(JsonNode jsonNode, Map<String, String> keysToClean) {
        for (Map.Entry<String, String> entry : keysToClean.entrySet()) {
            change(jsonNode, entry.getKey(), entry.getValue());
        }
    }

    private void change(JsonNode parent, String fieldName, String newValue) {
        if (parent.has(fieldName)) {
            ((ObjectNode) parent).put(fieldName, newValue);
        }

        //recursively invoke this method on all properties
        for (JsonNode child : parent) {
            change(child, fieldName, newValue);
        }
    }

    public String getName() {
        return "json-cleaner";
    }

}