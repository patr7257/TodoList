package dk.dtu.shared;

public enum TaskStatus {
    NOT_STARTED(0),
    IN_PROGRESS(50),
    DELAYED(50),
    NEED_HELP(50),
    DONE(100);

    private final int completionPercentage;

    TaskStatus(int completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    public int getCompletionPercentage() {
        return completionPercentage;
    }
}