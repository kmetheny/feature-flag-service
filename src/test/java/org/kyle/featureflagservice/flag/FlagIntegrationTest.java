package org.kyle.featureflagservice.flag;

import com.jayway.jsonpath.JsonPath;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "flags.environments=dev,prod")
class FlagIntegrationTest {

    private record PageResponse<T>(List<T> content) {
    }

    private Long saveFlag(String name) {
        Flag flag = new Flag();
        flag.setName(name);
        flag.setEnabled(false);
        flag.setEnvironments(Set.of("dev"));
        flag.setRolloutPercentage(100);
        return Objects.requireNonNull(repo.save(flag).getId());
    }

    @SuppressWarnings("DataFlowIssue")
    private static FlagRequest request(@Nullable String name, @Nullable String description) {
        return new FlagRequest(name, description, true, Set.of("dev"), 100);
    }

    private ResultActions postJson(@Nullable String json) throws Exception {
        var request = post("/flags").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
        return mvc.perform(json == null ? request : request.content(json));
    }

    private ResultActions postFlag(FlagRequest body) throws Exception {
        return postJson(mapper.writeValueAsString(body));
    }

    private ResultActions putJson(long id, @Nullable String json) throws Exception {
        var request = put("/flags/{id}", id).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
        return mvc.perform(json == null ? request : request.content(json));
    }

    private ResultActions putFlag(long id, FlagRequest body) throws Exception {
        return putJson(id, mapper.writeValueAsString(body));
    }

    private ResultActions getEvaluation(String flagName, String userId, String environment) throws Exception {
        return mvc.perform(get("/flags/{flagName}/evaluate", flagName)
                .param("userId", userId)
                .param("env", environment)
                .accept(MediaType.APPLICATION_JSON));
    }

    private static ResultMatcher problem(HttpStatus expected) {
        return result -> {
            status().is(expected.value()).match(result);
            content().contentType(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.status").value(expected.value()).match(result);
        };
    }

    @SuppressWarnings("DataFlowIssue")
    private static Stream<Arguments> badFlagRequestBodyArgs() {
        String tooLongValue = "x".repeat(256);
        return Stream.of(
                Arguments.of(request(null, null), "name"),
                Arguments.of(request("", null), "name"),
                Arguments.of(request("Test-name", null), "name"),
                Arguments.of(request(tooLongValue, null), "name"),
                Arguments.of(request("test-name", tooLongValue), "description"),
                Arguments.of(new FlagRequest("test-name", null, true, Set.of("dev"), -1), "rolloutPercentage"),
                Arguments.of(new FlagRequest("test-name", null, true, Set.of("dev"), 101), "rolloutPercentage"),
                Arguments.of(new FlagRequest("test-name", null, true, Set.of("dev"), null), "rolloutPercentage"),
                Arguments.of(new FlagRequest("test-name", null, true, null, 50), "environments"),
                Arguments.of(new FlagRequest("test-name", null, true, Set.of(""), 50), "environments[]"),
                Arguments.of(new FlagRequest("test-name", null, true, Set.of("Dev"), 50), "environments[]"),
                Arguments.of(new FlagRequest("test-name", null, null, Set.of("dev"), 50), "enabled")
        );
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FlagRepository repo;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
    }

    @Test
    void testGetFlags() throws Exception {
        String flagNameFormat = "test-flag-%05d";
        int numElements = 100;
        // Insert in reverse order to test sorting
        for (int i = numElements - 1; i >= 0; i--) {
            saveFlag(String.format(flagNameFormat, i));
        }

        Pattern nextUriPattern = Pattern.compile("<([^>]*)>\\s*;\\s*rel=\"next\"");
        Matcher m = nextUriPattern.matcher("</flags>; rel=\"next\"");
        List<FlagResponse> flags = new ArrayList<>(100);
        while (m.find()) {
            String nextUri = m.group(1);
            MockHttpServletResponse response = mvc.perform(get(nextUri).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse();

            String json = response.getContentAsString();
            PageResponse<FlagResponse> flagPage = mapper.readValue(json, new TypeReference<>() {
            });
            flags.addAll(flagPage.content());

            String linkHeader = response.getHeader(HttpHeaders.LINK);
            assertThat(linkHeader).isNotNull();
            m = nextUriPattern.matcher(linkHeader);
        }

        assertThat(flags).extracting(FlagResponse::name)
                .hasSize(numElements)
                .doesNotHaveDuplicates()
                .isSorted();
    }

    @Test
    void testGetFlagById() throws Exception {
        String flagName = "test-flag";
        Long id = saveFlag(flagName);

        mvc.perform(get("/flags/{id}", id).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(flagName))
                .andExpect(jsonPath("$.environments[0]").value("dev"))
                .andExpect(jsonPath("$.rolloutPercentage").value(100));
    }

    @Test
    void testGetFlagById_NotFound() throws Exception {
        long flagId = 9999L;

        mvc.perform(get("/flags/{id}", flagId).accept(MediaType.APPLICATION_JSON))
                .andExpect(problem(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value("Flag not found: " + flagId));
    }

    @Test
    void testEvaluateFlag_On() throws Exception {
        String flagName = "test-flag";
        String environment = "dev";
        String userId = "test-user";
        Flag flag = new Flag();
        flag.setName(flagName);
        flag.setEnvironments(Set.of(environment));
        flag.setEnabled(true);
        flag.setRolloutPercentage(100);
        repo.save(flag);

        getEvaluation(flagName, userId, environment)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.flag").value(flagName))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.environment").value(environment))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void testEvaluateFlag_Off() throws Exception {
        String flagName = "test-flag";
        String environment = "dev";
        String userId = "test-user";
        Flag flag = new Flag();
        flag.setName(flagName);
        flag.setEnabled(true);
        flag.setEnvironments(Set.of("prod"));
        flag.setRolloutPercentage(100);
        repo.save(flag);

        getEvaluation(flagName, userId, environment)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.flag").value(flagName))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.environment").value(environment))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void testEvaluateFlag_NotFound() throws Exception {
        String flagName = "test-flag";
        getEvaluation(flagName, "123456", "dev")
                .andExpect(problem(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value("Flag not found: " + flagName));
    }

    @Test
    void testEvaluateFlag_UnknownEnvironment() throws Exception {
        String flagName = "test-flag";
        String environment = "pord";
        Flag flag = new Flag();
        flag.setName(flagName);
        flag.setEnabled(true);
        flag.setEnvironments(Set.of(environment));
        flag.setRolloutPercentage(100);
        repo.save(flag);

        getEvaluation(flagName, "123456", environment)
                .andExpect(problem(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("Unknown environments: [" + environment + "]"));
    }

    @Test
    void testCreateFlag() throws Exception {
        String flagName = "test-flag";
        String flagDescription = "This is a test flag";
        String environment = "dev";
        boolean enabled = true;
        int rolloutPercentage = 100;

        MvcResult result = postFlag(new FlagRequest(flagName, flagDescription, enabled, Set.of(environment), rolloutPercentage))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value(flagName))
                .andExpect(jsonPath("$.description").value(flagDescription))
                .andExpect(jsonPath("$.environments[0]").value(environment))
                .andExpect(jsonPath("$.enabled").value(enabled))
                .andExpect(jsonPath("$.rolloutPercentage").value(rolloutPercentage))
                .andReturn();

        Flag saved = repo.findByName(flagName).orElseThrow();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("http://localhost/flags/" + saved.getId());
        String responseJson = result.getResponse().getContentAsString();
        assertThat(saved.getId()).isEqualTo(JsonPath.parse(responseJson).read("$.id", Long.class));
        assertThat(saved.getName()).isEqualTo(flagName);
        assertThat(saved.getDescription()).isEqualTo(flagDescription);
        assertThat(saved.getEnvironments()).containsExactlyInAnyOrderElementsOf(Set.of(environment));
        assertThat(saved.isEnabled()).isEqualTo(enabled);
        assertThat(saved.getRolloutPercentage()).isEqualTo(rolloutPercentage);
    }

    @Test
    void testCreateFlag_DuplicateName() throws Exception {
        String flagName = "test-flag";
        saveFlag(flagName);

        postFlag(request(flagName, null))
                .andExpect(problem(HttpStatus.CONFLICT))
                .andExpect(jsonPath("$.detail").value("Name already exists: " + flagName));

        assertThat(repo.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("badFlagRequestBodyArgs")
    void testCreateFlag_BadRequest(FlagRequest request, String invalidField) throws Exception {
        postFlag(request)
                .andExpect(problem(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.errors['" + invalidField + "']").exists());

        assertThat(repo.count()).isEqualTo(0);
    }

    @Test
    void testCreateFlag_UnknownEnvironment() throws Exception {
        String environment = "pord";

        postFlag(new FlagRequest("test-flag", null, false, Set.of(environment), 0))
                .andExpect(problem(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("Unknown environments: [" + environment + "]"));

        assertThat(repo.count()).isEqualTo(0);
    }

    @Test
    void testCreateFlag_MissingBody() throws Exception {
        postJson(null).andExpect(problem(HttpStatus.BAD_REQUEST));

        assertThat(repo.count()).isEqualTo(0);
    }

    @Test
    void testUpdateFlag() throws Exception {
        Long id = saveFlag("test-flag");

        String flagName = "updated-test-flag";
        String flagDescription = "This is an updated test flag";
        String environment = "prod";
        boolean enabled = true;
        int rolloutPercentage = 50;

        putFlag(id, new FlagRequest(flagName, flagDescription, enabled, Set.of(environment), rolloutPercentage))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value(flagName))
                .andExpect(jsonPath("$.description").value(flagDescription))
                .andExpect(jsonPath("$.environments[0]").value(environment))
                .andExpect(jsonPath("$.enabled").value(enabled))
                .andExpect(jsonPath("$.rolloutPercentage").value(rolloutPercentage));

        Flag updatedFlag = repo.findById(id).orElseThrow();
        assertThat(updatedFlag.getName()).isEqualTo(flagName);
        assertThat(updatedFlag.getDescription()).isEqualTo(flagDescription);
        assertThat(updatedFlag.getEnvironments()).containsExactlyInAnyOrderElementsOf(Set.of(environment));
        assertThat(updatedFlag.isEnabled()).isEqualTo(enabled);
        assertThat(updatedFlag.getRolloutPercentage()).isEqualTo(rolloutPercentage);
    }

    @Test
    void testUpdateFlag_SameName() throws Exception {
        String flagName = "test-flag";
        Long id = saveFlag(flagName);

        putFlag(id, request(flagName, null))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(flagName));

        Flag updatedFlag = repo.findById(id).orElseThrow();
        assertThat(updatedFlag.getName()).isEqualTo(flagName);
    }

    @Test
    void testUpdateFlag_DuplicateName() throws Exception {
        String originalName = "test-flag";
        Long id = saveFlag(originalName);

        String updatedName = "updated-test-flag";
        saveFlag(updatedName);

        putFlag(id, request(updatedName, null))
                .andExpect(problem(HttpStatus.CONFLICT))
                .andExpect(jsonPath("$.detail").value("Name already exists: " + updatedName));

        Flag repoFlag = repo.findById(id).orElseThrow();
        assertThat(repoFlag.getName()).isEqualTo(originalName);
    }

    @Test
    void testUpdateFlag_NotFound() throws Exception {
        long id = 9999L;

        putFlag(id, new FlagRequest("test-flag", null, false, Set.of("dev"), 0))
                .andExpect(problem(HttpStatus.NOT_FOUND))
                .andExpect(jsonPath("$.detail").value("Flag not found: " + id));
    }

    @Test
    void testUpdateFlag_UnknownEnvironment() throws Exception {
        String name = "test-flag";
        Long id = saveFlag(name);
        String environment = "pord";

        putFlag(id, new FlagRequest("updated-name", null, false, Set.of(environment), 0))
                .andExpect(problem(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.detail").value("Unknown environments: [" + environment + "]"));

        assertThat(repo.count()).isEqualTo(1);
        Flag flag = repo.findById(id).orElseThrow();
        assertThat(flag.getName()).isEqualTo(name);
    }

    @ParameterizedTest
    @MethodSource("badFlagRequestBodyArgs")
    void testUpdateFlag_BadRequest(FlagRequest request, String invalidField) throws Exception {
        String name = "test-flag";
        Long id = saveFlag(name);

        putFlag(id, request)
                .andExpect(problem(HttpStatus.BAD_REQUEST))
                .andExpect(jsonPath("$.errors['" + invalidField + "']").exists());

        assertThat(repo.count()).isEqualTo(1);
        Flag flag = repo.findById(id).orElseThrow();
        assertThat(flag.getName()).isEqualTo(name);
    }

    @Test
    void testUpdateFlag_MissingBody() throws Exception {
        putJson(1, null).andExpect(problem(HttpStatus.BAD_REQUEST));
    }

    @Test
    void testDeleteFlag() throws Exception {
        Long id = saveFlag("test-flag");

        mvc.perform(delete("/flags/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(repo.existsById(id)).isFalse();
    }

    @Test
    void testDeleteFlag_MissingId() throws Exception {
        mvc.perform(delete("/flags/9999"))
                .andExpect(status().isNoContent());
    }

}
