package com.quotagate.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quotagate.route.ModelRouteMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/v1")
public class ModelController {

    private final ModelRouteMapper modelRouteMapper;

    public ModelController(ModelRouteMapper modelRouteMapper) {
        this.modelRouteMapper = modelRouteMapper;
    }

    @GetMapping("/models")
    public ModelList models() {
        List<Model> models = modelRouteMapper.findEnabledAliases().stream()
                .map(alias -> new Model(alias, "model", Instant.now().getEpochSecond(), "quotagate"))
                .toList();
        return new ModelList("list", models);
    }

    public record ModelList(String object, List<Model> data) {
    }

    public record Model(
            String id,
            String object,
            long created,
            @JsonProperty("owned_by") String ownedBy
    ) {
    }
}
