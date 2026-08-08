package olap.ai.mcp.metamodel.models;

import lombok.Builder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Builder
public record CubeSchema(
        String name,
        String description,
        List<Cube> cubes
) {
    public CubeSchema {
        Objects.requireNonNull(name, "Schema name required");
        Objects.requireNonNull(cubes, "Cubes required");
        cubes = List.copyOf(cubes);
        description = description == null ? "" : description;

        // Optional: fail fast on duplicate names
        long distinct = cubes.stream()
                .map(c -> c.name().toLowerCase())
                .distinct()
                .count();
        if (distinct != cubes.size()) {
            throw new IllegalArgumentException("Duplicate cube names detected in schema: " + name);
        }
    }

    public Optional<Cube> getCube(String cubeName) {
        if (cubeName == null) return Optional.empty();
        return cubes.stream()
                .filter(c -> c.name().equalsIgnoreCase(cubeName))
                .findFirst();
    }

    public List<String> cubeNames() {
        return cubes.stream().map(Cube::name).toList();
    }

    /** Convenience map view (created on each call – fine for typical schema sizes) */
    public Map<String, Cube> asMap() {
        return cubes.stream()
                .collect(Collectors.toMap(
                        c -> c.name().toLowerCase(),
                        Function.identity()
                ));
    }
}