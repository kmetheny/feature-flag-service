package org.kyle.featureflagservice.flag;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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

    private static void checkUris(String linkHeader, List<String> sortParams, Page<?> page, Map<String, Integer> relPages) {
        Map<String, UriComponents> links = new HashMap<>();
        Matcher m = Pattern.compile("<([^>]*)>\\s*;\\s*rel=\"([^\"]*)\"").matcher(linkHeader);
        while (m.find()) {
            links.put(m.group(2), UriComponentsBuilder.fromUriString(m.group(1)).build());
        }
        assertThat(links).containsOnlyKeys(relPages.keySet());

        for (Map.Entry<String, Integer> relPage : relPages.entrySet()) {
            UriComponents uri = links.get(relPage.getKey());

            assertThat(uri.getPath()).isEqualTo("/flags");
            assertThat(uri.getQueryParams().getFirst("size"))
                    .isEqualTo(String.valueOf(page.getSize()));
            assertThat(uri.getQueryParams().getFirst("page"))
                    .isEqualTo(String.valueOf(relPage.getValue()));
            assertThat(uri.getQueryParams().get("sort")).isEqualTo(sortParams);

        }
    }

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

    private ResultMatcher pageModel(Page<?> page) {
        return result -> {
            status().isOk().match(result);
            header().exists(HttpHeaders.LINK).match(result);
            jsonPath("$.content").isArray().match(result);
            jsonPath("$.content", hasSize(page.getNumberOfElements())).match(result);
            if (!page.isEmpty()) {
                responseBodyMatches("$.content[0]").match(result);
            }
            jsonPath("$.page.size").value(page.getSize()).match(result);
            jsonPath("$.page.number").value(page.getNumber()).match(result);
            jsonPath("$.page.totalElements").value(page.getTotalElements()).match(result);
            jsonPath("$.page.totalPages").value(page.getTotalPages()).match(result);
        };
    }

    private static Stream<Arguments> findPageArgs() {
        PageRequest defaultRequest = PageRequest.of(0, 10, Sort.Direction.ASC, "name");

        return Stream.of(
                Arguments.argumentSet("Default params",
                        defaultRequest,
                        100, null, null,
                        Map.of("first", 0, "next", 1, "last", 9)),
                Arguments.argumentSet("Sort descending",
                        PageRequest.of(0, 10, Sort.Direction.DESC, "name"),
                        100, null, null,
                        Map.of("first", 0, "next", 1, "last", 9)),
                Arguments.argumentSet("Multiple sorts",
                        PageRequest.of(0, 10, Sort.Direction.ASC, "name", "description"),
                        100, null, null,
                        Map.of("first", 0, "next", 1, "last", 9)),
                Arguments.argumentSet("Page size over cap",
                        PageRequest.of(0, 200, Sort.Direction.ASC, "name"),
                        100, null, "500",
                        Map.of("first", 0, "last", 0)),
                Arguments.argumentSet("Invalid page number",
                        defaultRequest,
                        100, "abc", null,
                        Map.of("first", 0, "next", 1, "last", 9)),
                Arguments.argumentSet("Invalid page size",
                        defaultRequest,
                        100, null, "abc",
                        Map.of("first", 0, "next", 1, "last", 9)),
                Arguments.argumentSet("Last page",
                        PageRequest.of(9, 10, Sort.Direction.ASC, "name"),
                        100, "9", null,
                        Map.of("first", 0, "prev", 8, "last", 9)
                ),
                Arguments.argumentSet("Only page",
                        PageRequest.of(0, 100, Sort.Direction.ASC, "name"),
                        100, null, "100",
                        Map.of("first", 0, "last", 0)
                ),
                Arguments.argumentSet("Empty page",
                        defaultRequest,
                        0, null, null,
                        Map.of("first", 0, "last", 0)
                ),
                Arguments.argumentSet("Past last page",
                        PageRequest.of(10, 10, Sort.Direction.ASC, "name"),
                        20, "10", "10",
                        Map.of("first", 0, "prev", 1, "last", 1)
                ),
                Arguments.argumentSet("Past end of empty",
                        PageRequest.of(3, 10, Sort.Direction.ASC, "name"),
                        0, "3", null,
                        Map.of("first", 0, "prev", 0, "last", 0))
        );
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

    @ParameterizedTest
    @MethodSource("findPageArgs")
    void testGetFlags(PageRequest pageRequest, int total, @Nullable String pageNumber, @Nullable String pageSize, Map<String, Integer> relPages) throws Exception {
        int offset = pageRequest.getPageSize() * pageRequest.getPageNumber();
        int count = Math.clamp(total - offset, 0, pageRequest.getPageSize());
        List<FlagResponse> content = Collections.nCopies(count, response);
        Page<FlagResponse> page = new PageImpl<>(content, pageRequest, total);
        given(service.findAll(pageRequest)).willReturn(page);

        var requestBuilder = get("/flags").accept(MediaType.APPLICATION_JSON);
        if (pageNumber != null) {
            requestBuilder.queryParam("page", pageNumber);
        }
        if (pageSize != null) {
            requestBuilder.queryParam("size", pageSize);
        }
        List<String> sortParams = pageRequest.getSort()
                .stream().map(
                        sortOrder -> sortOrder.getProperty() + "," + sortOrder.getDirection().toString().toLowerCase()
                )
                .toList();
        for (String sortParam : sortParams) {
            requestBuilder.queryParam("sort", sortParam);
        }

        String linkHeader = mvc.perform(requestBuilder)
                .andExpect(pageModel(page))
                .andReturn().getResponse().getHeader(HttpHeaders.LINK);

        assertThat(linkHeader).isNotNull();
        checkUris(linkHeader, sortParams, page, relPages);
    }

    @Test
    void testGetFlags_InvalidSort() throws Exception {
        String invalidSort = "email";

        mvc.perform(get("/flags")
                        .accept(MediaType.APPLICATION_JSON)
                        .queryParam("sort", invalidSort))
                .andExpect(problem(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("Invalid sort: " + invalidSort));

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void testGetFlags_InvalidDirection() throws Exception {
        String invalidDir = "sideways";

        mvc.perform(get("/flags")
                        .accept(MediaType.APPLICATION_JSON)
                        .queryParam("sort", "name," + invalidDir))
                .andExpect(problem(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("Invalid sort: " + invalidDir));

        then(service).shouldHaveNoInteractions();
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
