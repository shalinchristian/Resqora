package com.resqora.resqora_backend.service.jobs;

// One job ad as returned by a job search provider (only the fields we use)
public record JobPosting(String title, String company, String location, String description, String url) {
}
