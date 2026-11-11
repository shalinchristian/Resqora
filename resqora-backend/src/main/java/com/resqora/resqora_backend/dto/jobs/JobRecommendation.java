package com.resqora.resqora_backend.dto.jobs;

// postedAt is just the date the source gives (YYYY-MM-DD), we don't make up a time
public record JobRecommendation(String title, String hiringName, String location, String why,
        String url, String applyUrl, String postedAt, String source) { }
