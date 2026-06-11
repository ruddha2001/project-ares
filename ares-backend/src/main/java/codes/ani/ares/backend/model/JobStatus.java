package codes.ani.ares.backend.model;

public enum JobStatus {
    INITIALIZED,
    PROCESSING,
    VECTOR_FETCH,
    ANALYZING,
    EXTRACTING_PROMPT_VECTOR,
    RETRIEVING_DATA,
    LIBRARIAN_PLANNING,
    COMPLETED,
    FAILED
}
