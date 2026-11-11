package com.resqora.resqora_backend.dto.jobs;

// postedAt is the source's ISO local date (YYYY-MM-DD), not an invented timestamp.
public record JobRecommendation(String title, String hiringName, String location, String why,
        String url, String applyUrl, String postedAt, String source) { }
