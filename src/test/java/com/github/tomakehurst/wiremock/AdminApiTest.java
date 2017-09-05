/*
 * Copyright (C) 2011 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.tomakehurst.wiremock;

import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.github.tomakehurst.wiremock.junit.Stubbing;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.testsupport.WireMockResponse;
import com.toomuchcoding.jsonassert.JsonAssertion;
import com.toomuchcoding.jsonassert.JsonVerifiable;
import org.apache.http.entity.StringEntity;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.apache.http.entity.ContentType.TEXT_PLAIN;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class AdminApiTest extends AcceptanceTestBase {

    static Stubbing dsl = wireMockServer;

    @Test
    public void getAllStubMappings() throws Exception {
        StubMapping stubMapping = dsl.stubFor(get(urlEqualTo("/my-test-url"))
            .willReturn(aResponse().withStatus(418)));

        String body = testClient.get("/__admin/mappings").content();

        JSONAssert.assertEquals(
            "{                                              \n" +
            "  \"mappings\" : [ {                           \n" +
            "    \"id\" : \"" + stubMapping.getId() + "\",  \n" +
            "    \"uuid\" : \"" + stubMapping.getId() + "\",\n" +
            "    \"request\" : {                            \n" +
            "      \"url\" : \"/my-test-url\",              \n" +
            "      \"method\" : \"GET\"                     \n" +
            "    },                                         \n" +
            "    \"response\" : {                           \n" +
            "      \"status\" : 418                         \n" +
            "    }                                          \n" +
            "  } ],                                         \n" +
            "                                               \n" +
            "  \"meta\": {                                  \n" +
            "    \"total\": 1                               \n" +
            "  }                                            \n" +
            "}",
        body,
            true
        );
    }

    @Test
    public void getAllStubMappingsWithLimitedResults() throws Exception {
        for (int i = 1; i <= 20; i++) {
            dsl.stubFor(get(urlEqualTo("/things/" + i)).willReturn(aResponse().withStatus(418)));
        }

        String allBody = testClient.get("/__admin/mappings").content();
        String limitedBody = testClient.get("/__admin/mappings?limit=7").content();

        JsonAssertion.assertThat(allBody).field("mappings").array().hasSize(20);
        JsonAssertion.assertThat(limitedBody).field("mappings").array().hasSize(7);
    }

    @Test
    public void getAllStubMappingsWithLimitedAndOffsetResults() throws Exception {
        for (int i = 1; i <= 20; i++) {
            dsl.stubFor(get(urlEqualTo("/things/" + i)).willReturn(aResponse().withStatus(418)));
        }

        String limitedBody = testClient.get("/__admin/mappings?limit=4&offset=3").content();

        JsonAssertion.assertThat(limitedBody).field("mappings").array().hasSize(4);
        JsonAssertion.assertThat(limitedBody).field("mappings").elementWithIndex(0)
            .field("request").field("url").isEqualTo("/things/17");
        JsonAssertion.assertThat(limitedBody).field("mappings").elementWithIndex(3)
            .field("request").field("url").isEqualTo("/things/14");
    }

    @Test
    public void deprecatedGetAllStubMappings() throws Exception {
        dsl.stubFor(get(urlEqualTo("/my-test-url")).willReturn(aResponse().withStatus(418)));

        String body = testClient.get("/__admin/").content();
        System.out.println(body);
        JSONAssert.assertEquals(
            "{\n" +
            "  \"mappings\" : [ {\n" +
            "    \"request\" : {\n" +
            "      \"url\" : \"/my-test-url\",\n" +
            "      \"method\" : \"GET\"\n" +
            "    },\n" +
            "    \"response\" : {\n" +
            "      \"status\" : 418\n" +
            "    }\n" +
            "  } ]\n" +
            "}",
            body,
            false
        );
    }

    @Test
    public void getStubMappingById() throws Exception {
        UUID id = UUID.randomUUID();

        dsl.stubFor(trace(urlEqualTo("/my-addressable-stub"))
            .withId(id)
            .willReturn(aResponse().withStatus(451))
        );

        String body = testClient.get("/__admin/mappings/" + id).content();

        JSONAssert.assertEquals(
            "{                                          \n" +
            "    \"id\": \""   + id + "\",              \n" +
            "    \"uuid\": \"" + id + "\",              \n" +
            "    \"request\" : {                        \n" +
            "      \"url\" : \"/my-addressable-stub\",  \n" +
            "      \"method\" : \"TRACE\"               \n" +
            "    },                                     \n" +
            "    \"response\" : {                       \n" +
            "      \"status\" : 451                     \n" +
            "    }                                      \n" +
            "}",
            body,
            true
        );
    }

    @Test
    public void getLoggedRequests() throws Exception {
        dsl.stubFor(get(urlPathEqualTo("/received-request/4")).willReturn(aResponse()));

        for (int i = 1; i <= 5; i++) {
            testClient.get("/received-request/" + i);
        }

        String body = testClient.get("/__admin/requests").content();

        System.out.println(body);
        JsonVerifiable check = JsonAssertion.assertThat(body);
        check.field("meta").field("total").isEqualTo(5);
        check.field("requests").elementWithIndex(2).field("request").field("url").isEqualTo("/received-request/3");
        check.field("requests").hasSize(5);
        check.field("requests").elementWithIndex(1).field("wasMatched").isEqualTo(true);
        check.field("requests").elementWithIndex(3).field("wasMatched").isEqualTo(false);
    }

    @Test
    public void getLoggedRequestsWithLimit() throws Exception {
        dsl.stubFor(get(urlPathEqualTo("/received-request/7"))
            .willReturn(aResponse().withStatus(200).withBody("This was matched")));

        for (int i = 1; i <= 7; i++) {
            testClient.get("/received-request/" + i);
        }

        String body = testClient.get("/__admin/requests?limit=2").content();

        JsonVerifiable check = JsonAssertion.assertThat(body);
        check.field("meta").field("total").isEqualTo(7);
        check.field("requests").elementWithIndex(0).field("request").field("url").isEqualTo("/received-request/7");
        check.field("requests").elementWithIndex(1).field("request").field("url").isEqualTo("/received-request/6");
        check.field("requests").hasSize(2);
    }

    @Test
    public void getLoggedRequestsWithLimitAndSinceDate() throws Exception {
        for (int i = 1; i <= 5; i++) {
            testClient.get("/received-request/" + i);
        }

        String midPoint = new ISO8601DateFormat().format(new Date());

        for (int i = 6; i <= 9; i++) {
            testClient.get("/received-request/" + i);
        }

        String body = testClient.get("/__admin/requests?since=" + midPoint + "&limit=3").content();

        JsonVerifiable check = JsonAssertion.assertThat(body);
        check.field("meta").field("total").isEqualTo(9);
        check.field("requests").hasSize(3);
        check.field("requests").elementWithIndex(0).field("request").field("url").isEqualTo("/received-request/9");
        check.field("requests").elementWithIndex(2).field("request").field("url").isEqualTo("/received-request/7");
    }

    @Test
    public void getLoggedRequestsWithLimitLargerThanResults() throws Exception {
        for (int i = 1; i <= 3; i++) {
            testClient.get("/received-request/" + i);
        }

        String body = testClient.get("/__admin/requests?limit=3000").content();

        JsonVerifiable check = JsonAssertion.assertThat(body);
        check.field("meta").field("total").isEqualTo(3);
        check.field("requests").hasSize(3);
    }

    @Test
    public void getLoggedRequestById() throws Exception {
        for (int i = 1; i <= 3; i++) {
            testClient.get("/received-request/" + i);
        }

        List<ServeEvent> serveEvents = dsl.getAllServeEvents();
        UUID servedStubId = serveEvents.get(1).getId();

        WireMockResponse response = testClient.get("/__admin/requests/" + servedStubId);
        String body = response.content();
        System.out.println("BODY:" + body);

        assertThat(response.statusCode(), is(200));
        JsonVerifiable check = JsonAssertion.assertThat(body);
        check.field("id").isEqualTo(servedStubId);
        check.field("request").field("url").isEqualTo("/received-request/2");
    }

    @Test
    public void deleteStubMappingById() throws Exception {
        StubMapping stubMapping = dsl.stubFor(get(urlPathEqualTo("/delete/this"))
            .willReturn(aResponse().withStatus(200))
        );

        assertThat(testClient.get("/delete/this").statusCode(), is(200));

        WireMockResponse response = testClient.delete("/__admin/mappings/" + stubMapping.getId());

        assertThat(response.content(), is("{}"));
        assertThat(testClient.get("/delete/this").statusCode(), is(404));
    }

    @Test
    public void returns404WhenAttemptingToDeleteNonExistentStubMapping() {
        assertThat(testClient.delete("/__admin/mappings/" + UUID.randomUUID()).statusCode(), is(404));
    }

    @Test
    public void editStubMappingById() throws Exception {
        StubMapping stubMapping = dsl.stubFor(get(urlPathEqualTo("/put/this"))
            .willReturn(aResponse().withStatus(200))
        );

        assertThat(testClient.get("/put/this").statusCode(), is(200));

        String requestBody =
            "{                                  \n" +
            "    \"request\": {                 \n" +
            "        \"method\": \"GET\",       \n" +
            "        \"url\": \"/put/this\"     \n" +
            "    },                             \n" +
            "    \"response\": {                \n" +
            "        \"status\": 418            \n" +
            "    }                              \n" +
            "}";

        WireMockResponse response = testClient.putWithBody(
            "/__admin/mappings/" + stubMapping.getId(),
            requestBody,
            "application/json"
        );

        JSONAssert.assertEquals(requestBody, response.content(), false);
        assertThat(testClient.get("/put/this").statusCode(), is(418));
    }

    @Test
    public void returns404WhenAttemptingToEditNonExistentStubMapping() {
        assertThat(
            testClient.putWithBody("/__admin/mappings/" + UUID.randomUUID(), "{}", "application/json"
        ).statusCode(), is(404));
    }

    @Test
    public void createStubMappingReturnsTheCreatedMapping() {
        WireMockResponse response = testClient.postJson("/__admin/mappings",
            "{                                \n" +
                "    \"name\": \"Teapot putter\",   \n" +
                "    \"request\": {                 \n" +
                "        \"method\": \"PUT\",       \n" +
                "        \"url\": \"/put/this\"     \n" +
                "    },                             \n" +
                "    \"response\": {                \n" +
                "        \"status\": 418            \n" +
                "    }                              \n" +
                "}"
        );

        assertThat(response.statusCode(), is(201));
        assertThat(response.firstHeader("Content-Type"), is("application/json"));
        String body = response.content();
        JsonAssertion.assertThat(body).field("id").matches("[a-z0-9\\-]{36}");
        JsonAssertion.assertThat(body).field("name").isEqualTo("Teapot putter");
    }

    @Test
    public void resetStubMappingsViaDELETE() {
        dsl.stubFor(get(urlEqualTo("/reset-this")).willReturn(aResponse().withStatus(200)));
        dsl.stubFor(get(urlEqualTo("/reset-this/too")).willReturn(aResponse().withStatus(200)));

        assertThat(testClient.get("/reset-this").statusCode(), is(200));
        assertThat(testClient.get("/reset-this/too").statusCode(), is(200));

        WireMockResponse response = testClient.delete("/__admin/mappings");

        assertThat(response.content(), is("{}"));
        assertThat(testClient.get("/reset-this").statusCode(), is(404));
        assertThat(testClient.get("/reset-this/too").statusCode(), is(404));
    }

    @Test
    public void resetRequestJournalViaDELETE() {
        testClient.get("/one");
        testClient.get("/two");
        testClient.get("/three");

        assertThat(dsl.getAllServeEvents().size(), is(3));

        WireMockResponse response = testClient.delete("/__admin/requests");

        assertThat(response.firstHeader("Content-Type"), is("application/json"));
        assertThat(response.content(), is("{}"));
        assertThat(response.statusCode(), is(200));
        assertThat(dsl.getAllServeEvents().size(), is(0));
    }

    @Test
    public void resetScenariosViaPOST() {
        dsl.stubFor(get(urlEqualTo("/stateful"))
            .inScenario("changing-states")
            .whenScenarioStateIs(STARTED)
            .willSetStateTo("final")
            .willReturn(aResponse().withBody("Initial")));

        dsl.stubFor(get(urlEqualTo("/stateful"))
            .inScenario("changing-states")
            .whenScenarioStateIs("final")
            .willReturn(aResponse().withBody("Final")));

        assertThat(testClient.get("/stateful").content(), is("Initial"));
        assertThat(testClient.get("/stateful").content(), is("Final"));

        WireMockResponse response = testClient.post("/__admin/scenarios/reset", new StringEntity("", TEXT_PLAIN));

        assertThat(response.content(), is("{}"));
        assertThat(response.firstHeader("Content-Type"), is("application/json"));
        assertThat(testClient.get("/stateful").content(), is("Initial"));
    }

    @Test
    public void defaultsUnspecifiedStubMappingAttributes() {
        WireMockResponse response = testClient.postJson("/__admin/mappings", "{}");

        assertThat(response.statusCode(), is(201));

        String body = response.content();
        JsonAssertion.assertThat(body).field("request").field("method").isEqualTo("ANY");
        JsonAssertion.assertThat(body).field("response").field("status").isEqualTo(200);

        assertThat(testClient.get("/").statusCode(), is(200));
    }

    @Test
    public void servesRamlSpec() {
        WireMockResponse response = testClient.get("/__admin/docs/raml");
        assertThat(response.statusCode(), is(200));
        assertThat(response.content(), containsString("#%RAML 0.8"));
    }

    @Test
    public void servesSwaggerSpec() {
        WireMockResponse response = testClient.get("/__admin/docs/swagger");
        assertThat(response.statusCode(), is(200));
        assertThat(response.content(), containsString("\"swagger\": \"2.0\""));
    }

    @Test
    public void servesSwaggerUiHtml() {
        WireMockResponse response = testClient.get("/__admin/swagger-ui/");
        assertThat(response.statusCode(), is(200));
    }

    @Test
    public void servesDocIndex() {
        WireMockResponse response = testClient.get("/__admin/docs");
        assertThat(response.statusCode(), is(200));
        assertThat(response.content(), containsString("<html"));
    }

}
