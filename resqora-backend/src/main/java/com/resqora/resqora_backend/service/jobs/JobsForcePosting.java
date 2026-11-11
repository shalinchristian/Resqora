package com.resqora.resqora_backend.service.jobs;

import java.time.LocalDate;

public record JobsForcePosting(String title, String hiringName, String city, String location,
        String summary, String url, String applyUrl, LocalDate postedAt) { }
