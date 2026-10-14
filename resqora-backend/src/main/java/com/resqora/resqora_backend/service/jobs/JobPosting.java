package com.resqora.resqora_backend.service.jobs;

// one job ad from a job search provider (only the fields we use)
public record JobPosting(String title, String company, String location, String description, String url) {
}
