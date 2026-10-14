package com.resqora.resqora_backend.demo;

import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.exception.DemoAccountReadOnlyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * When demo mode is on, the shared demo account is read-only for destructive changes
 * (delete resumes, delete account, change password, rename). Uploads and analysis still work;
 * everything is reset on the next startup anyway. With demo mode off it is an ordinary account.
 */
@Component
public class DemoAccountGuard {
    private final boolean demoEnabled;

    public DemoAccountGuard(@Value("${resqora.demo.enabled:false}") boolean demoEnabled) {
        this.demoEnabled = demoEnabled;
    }

    public boolean isDemo(User user) {
        return demoEnabled && user != null && DemoDataSeeder.DEMO_EMAIL.equalsIgnoreCase(user.getEmail());
    }

    public void rejectIfDemo(User user) {
        if (isDemo(user)) {
            throw new DemoAccountReadOnlyException();
        }
    }
}
