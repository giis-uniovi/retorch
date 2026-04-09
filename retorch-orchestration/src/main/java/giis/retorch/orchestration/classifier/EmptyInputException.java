package giis.retorch.orchestration.classifier;

/**
 * The {@code EmptyInputException}  is thrown when the {@code RetorchClassifier} receives an input that is empty
 * or its invalid i.e: null, no exist directories...
 */
public class EmptyInputException extends Exception {

    private static final long serialVersionUID = -6473170362328956807L;

    public EmptyInputException(String errorMessage) {
        super(errorMessage);
    }
}
