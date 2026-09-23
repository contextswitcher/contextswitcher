package com.contextswitcher.tasks;

// [impl->dsn~task-file-parsing~4]
public class TaskParseException extends Exception {

    public TaskParseException(String message) {
        super(message);
    }

    public TaskParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
