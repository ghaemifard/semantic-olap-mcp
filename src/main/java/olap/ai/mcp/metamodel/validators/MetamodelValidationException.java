package olap.ai.mcp.metamodel.validators;


public class MetamodelValidationException extends RuntimeException {
    public MetamodelValidationException(String message) {
        super(message);
    }

    public MetamodelValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}