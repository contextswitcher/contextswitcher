package com.contextswitcher.switching;

/// Outcome of one switch action; `detail` carries the failure cause
/// (e.g. ssh stderr) or a short success note.
public record ActionResult(boolean ok, String detail) {

    public static ActionResult success(String detail) {
        return new ActionResult(true, detail);
    }

    public static ActionResult failure(String detail) {
        return new ActionResult(false, detail);
    }
}
