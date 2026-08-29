package com.ankit.joblens.batchapi;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/duplicates")
public class DuplicateQueryController {

    private final DuplicateQueryRepository repository;

    public DuplicateQueryController(DuplicateQueryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Map<String, Object>> clusters() {
        return repository.findClusters();
    }

    @GetMapping("/{id}")
    public Map<String, Object> cluster(@PathVariable long id) {
        Map<String, Object> cluster = repository.findCluster(id);
        if (cluster == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Duplicate cluster not found");
        }
        Map<String, Object> result = new LinkedHashMap<>(cluster);
        result.put("members", repository.findClusterMembers(id));
        result.put("evidence", repository.findClusterEvidence(id));
        return result;
    }

    @GetMapping("/similarities")
    public List<Map<String, Object>> similarities(
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) BigDecimal minimumScore) {
        if (decision != null && !decision.equals("POSSIBLE_DUPLICATE")
                && !decision.equals("LIKELY_DUPLICATE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported similarity decision");
        }
        if (minimumScore != null
                && (minimumScore.signum() < 0 || minimumScore.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minimumScore must be between 0 and 100");
        }
        return repository.findSimilarities(decision, minimumScore);
    }

    @GetMapping("/similarities/{id}")
    public Map<String, Object> similarity(@PathVariable long id) {
        Map<String, Object> similarity = repository.findSimilarity(id);
        if (similarity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job similarity not found");
        }
        return similarity;
    }
}
