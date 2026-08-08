package olap.ai.mcp.metamodel.validators;


import java.util.List;
import java.util.stream.Collectors;

public record ValidationResult(List<ValidationError> errors) {

    public ValidationResult {
        errors = (errors == null) ? List.of() : List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.stream()
                .noneMatch(e -> e.severity() == ValidationSeverity.ERROR);
    }

    public boolean hasWarnings() {
        return errors.stream()
                .anyMatch(e -> e.severity() == ValidationSeverity.WARNING);
    }

    public List<ValidationError> errorsOnly() {
        return errors.stream()
                .filter(e -> e.severity() == ValidationSeverity.ERROR)
                .toList();
    }

    public List<ValidationError> warningsOnly() {
        return errors.stream()
                .filter(e -> e.severity() == ValidationSeverity.WARNING)
                .toList();
    }

    public void throwIfInvalid() {
        if (!isValid()) {
            String summary = errorsOnly().stream()
                    .map(e -> "[%s] %s – %s".formatted(e.path(), e.code(), e.message()))
                    .collect(Collectors.joining("\n"));
            throw new MetamodelValidationException(
                    "OLAP Metamodel Validation Failed:\n" + summary);
        }
    }
}