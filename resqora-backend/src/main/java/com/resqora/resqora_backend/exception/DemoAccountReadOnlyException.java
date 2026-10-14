package com.resqora.resqora_backend.exception;

// Demo mode: the shared demo account can't be deleted or have its password, name or resumes removed (403)
public class DemoAccountReadOnlyException extends RuntimeException {
    public DemoAccountReadOnlyException() {
        super("The demo account can't be changed");
    }
}
