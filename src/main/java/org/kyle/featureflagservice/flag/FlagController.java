package org.kyle.featureflagservice.flag;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/flags")
public class FlagController {

    private static final Set<String> SORTABLE = Set.of("name", "description", "enabled", "rolloutPercentage");
    private final FlagService service;

    public FlagController(FlagService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PagedModel<FlagResponse>> getAll(@PageableDefault(sort = "name") Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty());
            }
        });

        Page<FlagResponse> page = service.findAll(pageable);

        List<String> links = new ArrayList<>();
        links.add(link(0, pageable, "first"));
        int lastPage = Math.max(page.getTotalPages() - 1, 0);
        if (page.hasPrevious()) {
            links.add(link(Math.min(page.getNumber() - 1, lastPage), pageable, "prev"));
        }
        if (page.hasNext()) {
            links.add(link(page.getNumber() + 1, pageable, "next"));
        }
        links.add(link(lastPage, pageable, "last"));

        return ResponseEntity.ok()
                .header(HttpHeaders.LINK, String.join(", ", links))
                .body(new PagedModel<>(page));
    }

    @GetMapping("/{id}")
    public FlagResponse getFlagById(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{name}/evaluate")
    public EvaluationResponse evaluateFlag(@PathVariable String name, @RequestParam String userId, @RequestParam String env) {
        return service.evaluate(name, userId, env);
    }

    @PostMapping
    public ResponseEntity<FlagResponse> addFlag(@Valid @RequestBody FlagRequest request) {
        FlagResponse response = service.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public FlagResponse updateFlag(@PathVariable Long id, @Valid @RequestBody FlagRequest updateRequest) {
        return service.update(id, updateRequest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFlag(@PathVariable Long id) {
        service.delete(id);
    }

    private static String link(int pageNumber, Pageable pageable, String rel) {
        UriComponentsBuilder uriBuilder = ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", pageNumber)
                .replaceQueryParam("size", pageable.getPageSize())
                .replaceQueryParam("sort");
        for (Sort.Order order : pageable.getSort()) {
            String property = order.getProperty();
            String direction = order.getDirection().isAscending() ? "asc" : "desc";
            String sortQuery = property + "," + direction;
            uriBuilder.queryParam("sort", sortQuery);
        }

        return "<" + uriBuilder.toUriString() + ">; rel=\"" + rel + "\"";
    }
}
