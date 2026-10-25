package org.kyle.featureflagservice.flag;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FlagController.class)
class FlagControllerTest {

    private static final String ENVIRONMENT = "prod";
    private static final String UNKNOWN_ENVIRONMENT = "pord";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private FlagService service;

    private FlagResponse response;
    private FlagRequest request;
    private EvaluationResponse evaluationResponse;
    private FlagRequest unknownEnvironmentRequest;

    private static Stream<Arguments> badFlagRequestJsonArgs() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of("{"),
                Arguments.of("""
                        {
                            "name": "test-flag",
                            "enabled": true,
                            "environments": ["dev"],
                            "rolloutPercentage": "Fifty"
                        }""")
        );
    }

    @SuppressWarnings("DataFlowIssue")
    private static Stream<Arguments> badFlagRequestBodyArgs() {
        String tooLongValue = "x".repeat(256);
        Set<String> environments = Set.of(ENVIRONMENT);

        return Stream.of(
                Arguments.of(new FlagRequest(null, null, true, environments, 100), "name"),
                Arguments.of(new FlagRequest("", null, true, environments, 100), "name"),
                Arguments.of(new FlagRequest("Test-name", null, true, environments, 100), "name"),
                Arguments.of(new FlagRequest(tooLongValue, null, true, environments, 100), "name"),
                Arguments.of(new FlagRequest("test-name", tooLongValue, true, environments, 100), "description"),
                Arguments.of(new FlagRequest("test-name", null, true, environments, -1), "rolloutPercentage"),
                Arguments.of(new FlagRequest("test-name", null, true, environments, 101), "rolloutPercentage"),
                Arguments.of(new FlagRequest("test-name", null, true, environments, null), "rolloutPercentage"),
                Arguments.of(new FlagRequest("test-name", null, true, null, 50), "environments"),
                Arguments.of(new FlagRequest("test-name", null, true, Set.of(""), 50), "environments[]"),
                Arguments.of(new FlagRequest("test-name", null, true, Set.of("Dev"), 50), "environments[]"),
                Arguments.of(new FlagRequest("test-name", null, null, environments, 50), "enabled")
        );
    }

    private MockHttpServletRequestBuilder evaluate(@Nullable String userId, @Nullable String env) {
        MockHttpServletRequestBuilder builder = get("/flags/{flagName}/evaluate", evaluationResponse.flag())
                .accept(MediaType.APPLICATION_JSON);
        if (userId != null) {
            builder.param("userId", userId);
        }
        if (env != null) {
            builder.param("env", env);
        }

        return builder;
    }

    private MockHttpServletRequestBuilder postFlag(String json) {
        return post("/flags")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private MockHttpServletRequestBuilder postFlag(FlagRequest request) {
        return postFlag(mapper.writeValueAsString(request));
    }

    private MockHttpServletRequestBuilder putFlag(Object id, String json) {
        return put("/flags/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private MockHttpServletRequestBuilder putFlag(Object id, FlagRequest request) {
        return putFlag(id, mapper.writeValueAsString(request));
    }

    private ResultMatcher responseBodyMatches(String prefix) {
        return result -> {
            content().contentType(MediaType.APPLICATION_JSON).match(result);
            jsonPath(prefix + ".id").value(response.id()).match(result);
            jsonPath(prefix + ".name").value(response.name()).match(result);
            jsonPath(prefix + ".description").value(response.description()).match(result);
            jsonPath(prefix + ".environments[0]").value(ENVIRONMENT).match(result);
            jsonPath(prefix + ".enabled").value(response.enabled()).match(result);
            jsonPath(prefix + ".rolloutPercentage").value(response.rolloutPercentage()).match(result);
        };
    }

    private static ResultMatcher problem(HttpStatus expected) {
        return result -> {
            status().is(expected.value()).match(result);
            content().contentType(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.status").value(expected.value()).match(result);
        };
    }

    @BeforeEach
    void setUp() {
        response = new FlagResponse(
                1L, "test-flag",
                "Test flag",
                Set.of(ENVIRONMENT),
                true,
                100
        );
        request = new FlagRequest(
                response.name(),
                response.description(),
                response.enabled(),
                response.environments(),
                response.rolloutPercentage()
        );
        evaluationResponse = new EvaluationResponse(
                response.name(),
                "test-user",
                ENVIRONMENT,
                response.enabled()
        );
        unknownEnvironmentRequest = new FlagRequest(
                request.name(),
                request.description(),
                request.enabled(),
                Set.of(UNKNOWN_ENVIRONMENT),
                request.rolloutPercentage()
        );
    }

    @Test
    void testGetFlags() throws Exception {
        given(service.findAll()).willReturn(List.of(response));

        mvc.perform(get("/flags").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(responseBodyMatches("$[0]"));
    }

    @Test
    void testGetFlagById() throws Exception {
        given(service.get(response.id())).willReturn(response);

        mvc.perform(get("/flags/{id}", response.id()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(responseBodyMatches("$"));
    }

    @Test
    void testGetFlagById_NotFound() throws Exception {
        given(service.get(response.id())).willThrow(new FlagNotFoundException(response.id()));

        mvc.perform(get("/flags/{id}", response.id()).accept(MediaType.APPLICATION_JSON))
                .andExpect(problem(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value("Flag not found: " + response.id()));
    }

    @Test
    void testGetFlagById_InvalidId() throws Exception {
        mvc.perform(get("/flags/{id}", "abc").accept(MediaType.APPLICATION_JSON))
                .andExpect(problem(HttpStatus.BAD_REQUEST));

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void testEvaluateFlag() throws Exception {
        given(service.evaluate(evaluationResponse.flag(), evaluationResponse.userId(), evaluationResponse.environment()))
                .willReturn(evaluationResponse);

        mvc.perform(evaluate(evaluationResponse.userId(), evaluationResponse.environment()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.flag").value(evaluationResponse.flag()))
                .andExpect(jsonPath("$.userId").value(evaluationResponse.userId()))
                .andExpect(jsonPath("$.environment").value(evaluationResponse.environment()))
                .andExpect(jsonPath("$.enabled").value(evaluationResponse.enabled()));
    }

    @Test
    void testEvaluateFlag_NotFound() throws Exception {
        given(service.evaluate(evaluationResponse.flag(), evaluationResponse.userId(), evaluationResponse.environment()))
                .willThrow(new FlagNotFoundException(response.name()));

        mvc.perform(evaluate(evaluationResponse.userId(), evaluationResponse.environment()))
                .andExpect(problem(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value("Flag not found: " + evaluationResponse.flag()));
    }

    @Test
    void testEvaluateFlag_UnknownEnvironment() throws Exception {
        given(service.evaluate(evaluationResponse.flag(), evaluationResponse.userId(), UNKNOWN_ENVIRONMENT))
                .willThrow(new UnknownEnvironmentException(List.of(UNKNOWN_ENVIRONMENT)));

        mvc.perform(evaluate(evaluationResponse.userId(), UNKNOWN_ENVIRONMENT))
                .andExpect(problem(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("Unknown environments: [" + UNKNOWN_ENVIRONMENT + "]"));
    }

    @Test
    void testEvaluateFlag_MissingUserId() throws Exception {
        mvc.perform(evaluate(null, evaluationResponse.environment()))
                .andExpect(problem(HttpStatus.BAD_REQUEST));

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void testEvaluateFlag_MissingEnv() throws Exception {
        mvc.perform(evaluate(evaluationResponse.userId(), null))
                .andExpect(problem(HttpStatus.BAD_REQUEST));

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void testCreateFlag() throws Exception {
        given(service.create(request)).willReturn(response);

        mvc.perform(postFlag(request))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/flags/" + response.id()))
                .andExpect(responseBodyMatches("$"));
    }

    @Test
    void testCreateFlag_DuplicateFlag() throws Exception {
        given(service.create(request)).willThrow(new DuplicateFlagException(response.name()));

        mvc.perform(postFlag(request))
                .andExpect(problem(HttpStatus.CONFLICT))
                .andExpect(jsonPath("$.detail").value("Name already exists: " + response.name()));
    }

    @Test
    void testCreateFlag_UnknownEnvironment() throws Exception {
        given(service.create(unknownEnvironmentRequest))
                .willThrow(new UnknownEnvironmentException(List.of(UNKNOWN_ENVIRONMENT)));

        mvc.perform(postFlag(unknownEnvironmentRequest))
                .andExpect(problem(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("Unknown environments: [" + UNKNOWN_ENVIRONMENT + "]"));
    }

    @ParameterizedTest
    @MethodSource("badFlagRequestJsonArgs")
    void testCreateFlag_BadJson(String json) throws Exception {
        mvc.perform(postFlag(json))
                .andExpect(problem(HttpStatus.BAD_REQUEST));

        then(service).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @MethodSource("badFlagRequestBodyArgs")
    void testCreateFlag_BadRequest(FlagRequest request, String invalidField) throws Exception {
        mvc.perform(postFlag(request))
                .andExpect(problem(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.errors['" + invalidField + "']").exists());

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void testUpdateFlag() throws Exception {
        given(service.update(response.id(), request)).willReturn(response);

        mvc.perform(putFlag(response.id(), request))
                .andExpect(status().isOk())
                .andExpect(responseBodyMatches("$"));
    }

    @Test
    void testUpdateFlag_NotFound() throws Exception {
        given(service.update(response.id(), request)).willThrow(new FlagNotFoundException(response.id()));

        mvc.perform(putFlag(response.id(), request))
                .andExpect(problem(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value("Flag not found: " + response.id()));
    }

    @Test
    void testUpdateFlag_DuplicateFlag() throws Exception {
        given(service.update(response.id(), request)).willThrow(new DuplicateFlagException(response.name()));

        mvc.perform(putFlag(response.id(), request))
                .andExpect(problem(HttpStatus.CONFLICT))
                .andExpect(jsonPath("$.detail").value("Name already exists: " + response.name()));
    }

    @Test
    void testUpdateFlag_UnknownEnvironment() throws Exception {
        given(service.update(response.id(), unknownEnvironmentRequest))
                .willThrow(new UnknownEnvironmentException(List.of(UNKNOWN_ENVIRONMENT)));

        mvc.perform(putFlag(response.id(), unknownEnvironmentRequest))
                .andExpect(problem(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("Unknown environments: [" + UNKNOWN_ENVIRONMENT + "]"));
    }

    @Test
    void testUpdateFlag_InvalidId() throws Exception {
        mvc.perform(putFlag("abc", request))
                .andExpect(problem(HttpStatus.BAD_REQUEST));

        then(service).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @MethodSource("badFlagRequestJsonArgs")
    void testUpdateFlag_BadJson(String json) throws Exception {
        mvc.perform(putFlag(response.id(), json))
                .andExpect(problem(HttpStatus.BAD_REQUEST));

        then(service).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @MethodSource("badFlagRequestBodyArgs")
    void testUpdateFlag_BadRequest(FlagRequest request, String invalidField) throws Exception {
        mvc.perform(putFlag(response.id(), request))
                .andExpect(problem(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.errors['" + invalidField + "']").exists());

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void testDeleteFlag() throws Exception {
        mvc.perform(delete("/flags/{id}", response.id()))
                .andExpect(status().isNoContent());

        then(service).should().delete(response.id());
    }

    @Test
    void testDeleteFlag_InvalidId() throws Exception {
        mvc.perform(delete("/flags/{id}", "abc"))
                .andExpect(problem(HttpStatus.BAD_REQUEST));

        then(service).shouldHaveNoInteractions();
    }
}
