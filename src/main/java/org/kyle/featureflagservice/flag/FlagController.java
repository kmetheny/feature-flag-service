package org.kyle.featureflagservice.flag;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/flags")
public class FlagController {

    private final FlagService service;

    public FlagController(FlagService service) {
        this.service = service;
    }

    @GetMapping
    public List<FlagResponse> getAll() {
        return service.findAll();
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
}
