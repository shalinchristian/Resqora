package com.resqora.resqora_backend.service.jobs;

import java.time.LocalDate;

// summary is the text we match against resume skills: the search summary, plus the full listing
// (description, responsibilities, qualifications, skills) once JobRecommendationService fetches it.
// id is the JobsForce job id used to fetch that listing, null if we don't have it
public record JobsForcePosting(String title, String hiringName, String city, String location,
        String summary, String url, String applyUrl, LocalDate postedAt, String id) {
    public JobsForcePosting(String title, String hiringName, String city, String location,
            String summary, String url, String applyUrl, LocalDate postedAt) {
        this(title, hiringName, city, location, summary, url, applyUrl, postedAt, null);
    }
    JobsForcePosting withDetails(String details) {
        if (details == null || details.isBlank()) return this;
        return new JobsForcePosting(title, hiringName, city, location, (summary + " " + details).strip(),
                url, applyUrl, postedAt, id);
    }
}
