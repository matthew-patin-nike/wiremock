package com.nike.cpspub;

import com.github.tomakehurst.wiremock.extension.*;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.io.IOException;

/**
 * Created by mpatin on 12/27/16.
 */
public class DataCleaner extends ResponseTransformer {

    private List<String> keysToClean;
    private Map<String, Map<String, String>> valuesToMapByDivisionKey;
    private String mockBaseUrl;
    private String cpsPubUrl;

    @Override
    public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
        if (response.getStatus() != 200) {
            return response;
        }

        keysToClean = (List<String>) parameters.get("keysToClean");
        if (keysToClean.size() == 0) {
            throw new RuntimeException("Transformer missing keysToClean, add them to options.json");
        }

        valuesToMapByDivisionKey = (Map<String, Map<String, String>>) parameters.get("valuesToMapByDivisionKey");
        if (valuesToMapByDivisionKey.size() == 0) {
            throw new RuntimeException("Transformer missing valuesToMapByDivisionKey, add them to options.json");
        }

        mockBaseUrl = (String) parameters.get("mockBaseUrl");
        if (mockBaseUrl == null) {
            throw new RuntimeException("Transformer missing mockBaseUrl, add it to options.json");
        }

        cpsPubUrl =  (String) parameters.get("cpsPubUrl");
        if (cpsPubUrl == null) {
            throw new RuntimeException("Transformer missing cpsPubUrl, add it to options.json");
        }

        String cleaned = ""; //we'd rather return nothing than unclean json.
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode responseJson = (JsonNode) mapper.readTree(response.getBody());
            cleanAllKeys(responseJson, "TEST PRODUCT FALLBACK VALUE"); // clean everything to be sure, then do fancy processing by uri
            if (request.getAbsoluteUrl().toLowerCase().contains("globalOfferings".toLowerCase())) {
                setSpecificGlobalOfferingKeys(responseJson);
            } else if (request.getAbsoluteUrl().toLowerCase().contains("products".toLowerCase())) {
                setSpecificProductsKeys(responseJson);
            }
            cleaned = responseJson.toString();
        } catch (IOException io) {
           io.printStackTrace();
        }

        return Response.Builder.like(response)
                .but().body(cleaned)
                .build();
    }

    private void cleanAllKeys(JsonNode jsonNode, String value) {
        for (String key : keysToClean) {
            change(jsonNode, key, value);
        }
    }

    private void change(JsonNode parent, String fieldName, String value) {
        if (parent.has(fieldName)) {
            ((ObjectNode) parent).put(fieldName, value);
        }

        //recursively invoke this method on all properties
        for (JsonNode child : parent) {
            change(child, fieldName, value);
        }
    }

    private void setSpecificGlobalOfferingKeys(JsonNode parent) {

        fixLinks(parent);

        JsonNode globalOfferings = parent.get("content");
        for (final JsonNode objNode : globalOfferings) {
            try {
                JsonNode modelOffering = objNode.get("modelOffering");
                String divCd = modelOffering.get("divCd").textValue();
                int mdlId = modelOffering.get("model").get("mdlId").asInt();
                cleanAllKeys(modelOffering, getValueFromDivAndModelId(divCd, mdlId));
            } catch (Exception ex) {
                ex.printStackTrace();
                // do nothing, we got everything already with the fallback, this is just to make it pretty
            }
        }
    }

    private void fixLinks(JsonNode parent) {
        JsonNode links = parent.get("links");
        for (JsonNode link : links) {
            String href = link.get("href").asText();
            if (! href.isEmpty()) {
                ObjectNode modifiable = (ObjectNode) link;
                modifiable.put("href", href.replace(cpsPubUrl, mockBaseUrl));
            }
        }
    }

    private void setSpecificProductsKeys(JsonNode parent) {
        try {
            String divCd = parent.get("divCd").textValue();
            int mdlId = parent.get("model").get("mdlId").asInt();
            cleanAllKeys(parent, getValueFromDivAndModelId(divCd, mdlId));
        } catch (Exception ex) {
            ex.printStackTrace();
            // do nothing, we got everything already with the fallback, this is just to make it pretty
        }
    }

    private String getValueFromDivAndModelId(String divCd, int mdlId) {
        //System.out.println(divCd + " " + mdlId);
        return valuesToMapByDivisionKey.get(divCd).get((mdlId % 10) + "");
    }

    public String getName() {
        return "json-cleaner";
    }

}