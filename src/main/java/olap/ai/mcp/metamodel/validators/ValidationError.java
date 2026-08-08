package olap.ai.mcp.metamodel.validators;


import lombok.Builder;
import java.util.Objects;

@Builder
public record ValidationError(
        String code,
        String message,
        String path,
        ValidationSeverity severity
) {
    public ValidationError {
        Objects.requireNonNull(code, "code required");
        Objects.requireNonNull(message, "message required");
        Objects.requireNonNull(path, "path required");
        if (severity == null) {
            severity = ValidationSeverity.ERROR;
        }
    }

    public static ValidationError error(String code, String message, String path) {
        return new ValidationError(code, message, path, ValidationSeverity.ERROR);
    }

    public static ValidationError warning(String code, String message, String path) {
        return new ValidationError(code, message, path, ValidationSeverity.WARNING);
    }
}
