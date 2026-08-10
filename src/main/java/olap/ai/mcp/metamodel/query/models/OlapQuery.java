package olap.ai.mcp.metamodel.query.models;


import lombok.Builder;
import olap.ai.mcp.metamodel.query.enums.AxisType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Builder
public record OlapQuery(
        String cubeName,
        List<String> measureNames,
        List<DrillLevel> drillLevels,       // grouping attributes placed on axes
        List<FilterPredicate> filters,      // slicers
        List<SortSpec> sorts,
        boolean nonEmpty,                   // suppress empty cells (common OLAP behaviour)
        Integer limit,
        Integer offset
) {
    public OlapQuery {
        Objects.requireNonNull(cubeName, "cubeName required");

        measureNames = (measureNames == null) ? List.of() : List.copyOf(measureNames);
        drillLevels  = (drillLevels  == null) ? List.of() : List.copyOf(drillLevels);
        filters      = (filters      == null) ? List.of() : List.copyOf(filters);
        sorts        = (sorts        == null) ? List.of() : List.copyOf(sorts);

        if (limit != null && limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        if (offset != null && offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
    }

    // ------------------------------------------------------------------
    // Convenience accessors
    // ------------------------------------------------------------------

    public List<DrillLevel> levelsOn(AxisType axis) {
        return drillLevels.stream()
                .filter(d -> d.axis() == axis)
                .toList();
    }

    public List<DrillLevel> rowLevels() {
        return levelsOn(AxisType.ROWS);
    }

    public List<DrillLevel> columnLevels() {
        return levelsOn(AxisType.COLUMNS);
    }

    // ------------------------------------------------------------------
    // Fluent transformation operations (return new immutable instances)
    // ------------------------------------------------------------------

    /** Replace the whole measure list. */
    public OlapQuery withMeasures(List<String> measures) {
        return copyBuilder().measureNames(measures).build();
    }

    public OlapQuery withMeasures(String... measures) {
        return withMeasures(List.of(measures));
    }

    /** Add one more measure (no duplicates). */
    public OlapQuery addMeasure(String measureName) {
        if (measureNames.stream().anyMatch(m -> m.equalsIgnoreCase(measureName))) {
            return this;
        }
        List<String> updated = new ArrayList<>(measureNames);
        updated.add(measureName);
        return copyBuilder().measureNames(updated).build();
    }

    /**
     * Slice – put a single equality filter on a dimension level
     * (replaces any previous filter on the same dimension+level).
     */
    public OlapQuery slice(String dimension, String level, Object value) {
        List<FilterPredicate> updated = filters.stream()
                .filter(f -> !(f.dimensionName().equalsIgnoreCase(dimension)
                        && f.levelName().equalsIgnoreCase(level)))
                .collect(Collectors.toCollection(ArrayList::new));

        updated.add(FilterPredicate.equals(dimension, level, value));
        return copyBuilder().filters(updated).build();
    }

    /**
     * Dice – add several filter predicates.
     * Existing filters on the same dimension+level are replaced.
     */
    public OlapQuery dice(List<FilterPredicate> predicates) {
        List<FilterPredicate> updated = new ArrayList<>(filters);

        for (FilterPredicate incoming : predicates) {
            updated.removeIf(f ->
                    f.dimensionName().equalsIgnoreCase(incoming.dimensionName())
                            && f.levelName().equalsIgnoreCase(incoming.levelName()));
            updated.add(incoming);
        }
        return copyBuilder().filters(updated).build();
    }

    public OlapQuery dice(FilterPredicate... predicates) {
        return dice(List.of(predicates));
    }

    /** Clear all filters. */
    public OlapQuery clearFilters() {
        return copyBuilder().filters(List.of()).build();
    }

    /**
     * Drill-down – add a level to the given axis (default = ROWS).
     * Does nothing if the exact same DrillLevel is already present.
     */
    public OlapQuery drillDown(String dimension, String hierarchy, String level) {
        return drillDown(dimension, hierarchy, level, AxisType.ROWS);
    }

    public OlapQuery drillDown(String dimension, String hierarchy,
                               String level, AxisType axis) {
        DrillLevel target = new DrillLevel(dimension, hierarchy, level, axis);
        if (drillLevels.contains(target)) {
            return this;
        }
        List<DrillLevel> updated = new ArrayList<>(drillLevels);
        updated.add(target);
        return copyBuilder().drillLevels(updated).build();
    }

    /**
     * Drill-up – remove a specific level from the query.
     */
    public OlapQuery drillUp(String dimension, String hierarchy, String level) {
        List<DrillLevel> updated = drillLevels.stream()
                .filter(d -> !(d.dimensionName().equalsIgnoreCase(dimension)
                        && d.hierarchyName().equalsIgnoreCase(hierarchy)
                        && d.levelName().equalsIgnoreCase(level)))
                .toList();
        return copyBuilder().drillLevels(updated).build();
    }

    /** Move an existing level from one axis to another (or change axis). */
    public OlapQuery pivot(String dimension, String hierarchy,
                           String level, AxisType newAxis) {
        List<DrillLevel> updated = drillLevels.stream()
                .map(d -> {
                    if (d.dimensionName().equalsIgnoreCase(dimension)
                            && d.hierarchyName().equalsIgnoreCase(hierarchy)
                            && d.levelName().equalsIgnoreCase(level)) {
                        return new DrillLevel(d.dimensionName(), d.hierarchyName(),
                                d.levelName(), newAxis);
                    }
                    return d;
                })
                .toList();
        return copyBuilder().drillLevels(updated).build();
    }

    public OlapQuery withSorts(List<SortSpec> sortSpecs) {
        return copyBuilder().sorts(sortSpecs).build();
    }

    public OlapQuery withSorts(SortSpec... sortSpecs) {
        return withSorts(List.of(sortSpecs));
    }

    public OlapQuery nonEmpty(boolean value) {
        return copyBuilder().nonEmpty(value).build();
    }

    public OlapQuery withLimit(Integer limit) {
        return copyBuilder().limit(limit).build();
    }

    public OlapQuery withOffset(Integer offset) {
        return copyBuilder().offset(offset).build();
    }

    // ------------------------------------------------------------------
    // Internal helper
    // ------------------------------------------------------------------

    private OlapQueryBuilder copyBuilder() {
        return OlapQuery.builder()
                .cubeName(this.cubeName)
                .measureNames(this.measureNames)
                .drillLevels(this.drillLevels)
                .filters(this.filters)
                .sorts(this.sorts)
                .nonEmpty(this.nonEmpty)
                .limit(this.limit)
                .offset(this.offset);
    }
}