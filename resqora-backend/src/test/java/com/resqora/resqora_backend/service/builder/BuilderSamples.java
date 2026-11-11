package com.resqora.resqora_backend.service.builder;

import com.resqora.resqora_backend.dto.builder.BuilderExportRequest;
import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ProjectEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Complete sample resumes, one per template, used for the format picker thumbnails.
final class BuilderSamples {
    private BuilderSamples() {
    }

    static Map<String, BuilderExportRequest> all() {
        Map<String, BuilderExportRequest> samples = new LinkedHashMap<>();
        samples.put("classic-aaron-dsouza.docx", classic());
        samples.put("combination-priya-sharma.docx", combination());
        samples.put("campus-joel-thomas.docx", campus());
        samples.put("centered-lydia-abraham.docx", centered());
        samples.put("header-band-arjun-mehta.docx", headerBand());
        return samples;
    }

    static BuilderExportRequest classic() {
        return new BuilderExportRequest("classic", "Aaron D'Souza", null,
                "aaron.dsouza@example.com", "+91 98765 43210", "Mumbai, Maharashtra", null, null,
                "Backend developer with two years of experience building Java and Spring Boot services for "
                        + "payments and logistics products. Comfortable owning an API from design to production "
                        + "monitoring, and keen on clean code, tests, and fast incident response.",
                List.of("Java", "Spring Boot", "REST APIs", "PostgreSQL", "MySQL", "Redis", "Kafka", "Docker",
                        "AWS (EC2, S3, RDS)", "JUnit", "Mockito", "Git", "Jenkins", "Linux", "SQL tuning"),
                List.of(
                        new ExperienceEntry("Paynest Technologies, Mumbai", "Software Engineer",
                                "Jul 2024 – Present",
                                """
                                Built the refund and settlement APIs in Spring Boot, handling about 40,000 \
                                transactions a day for 300+ merchants.
                                Cut p95 response time of the merchant ledger API from 820 ms to 240 ms by adding \
                                Redis caching and rewriting two slow PostgreSQL queries.
                                Moved nightly reconciliation from cron scripts to Kafka consumers, reducing manual \
                                follow-ups by the finance team by around 60%.
                                Raised unit test coverage on the payments module from 48% to 81% with JUnit and Mockito.
                                Joined the on-call rotation and wrote runbooks for the five most frequent alerts."""),
                        new ExperienceEntry("ShipKart Logistics, Pune", "Software Engineering Intern",
                                "Jan 2024 – Jun 2024",
                                """
                                Developed a shipment tracking microservice that pushed status updates to customers \
                                over SMS and email.
                                Containerised three internal services with Docker and set up Jenkins pipelines for them.
                                Wrote integration tests for courier partner webhooks, catching four mapping bugs \
                                before release.""")),
                List.of(new EducationEntry("St. Francis Institute of Technology, University of Mumbai",
                        "B.E. in Computer Engineering — CGPA 8.4/10", "2020 – 2024",
                        """
                        Coursework: Data Structures, DBMS, Operating Systems, Computer Networks""", "Mumbai")),
                List.of(
                        new ProjectEntry("Expense Splitter API",
                                """
                                Spring Boot and PostgreSQL REST API for group expenses with JWT login and \
                                settle-up suggestions.
                                Deployed on AWS EC2 with RDS; used by a 30-member hostel group for a semester."""),
                        new ProjectEntry("Library Seat Booking System",
                                """
                                Booking service for the college reading room with time slots and a waitlist.
                                Reduced double-bookings at peak exam time to zero in its first month."""),
                        new ProjectEntry("URL Shortener with Analytics",
                                """
                                Spring Boot service with Redis redirects and daily click counts, load-tested with JMeter.""")),
                List.of("Oracle Certified Associate, Java SE 8 Programmer", "AWS Certified Cloud Practitioner"));
    }

    static BuilderExportRequest combination() {
        return new BuilderExportRequest("combination", "Priya Sharma", null,
                "priya.sharma@example.com", "+91 91234 56789", "Gurugram, Haryana", null, null,
                "Data analyst with 18 months of experience turning sales and customer data into dashboards and "
                        + "decisions. Strong in SQL, Python, and Power BI, and used to working directly with "
                        + "business teams to define metrics and answer questions quickly.",
                List.of("SQL", "Python (pandas, NumPy)", "Power BI", "Tableau", "Advanced Excel",
                        "Google Analytics", "A/B testing", "Statistics", "BigQuery", "ETL", "Data cleaning",
                        "Stakeholder reporting"),
                List.of(
                        new ExperienceEntry("Freshcart Retail Pvt. Ltd., Gurugram", "Data Analyst",
                                "Jun 2024 – Present",
                                """
                                Built a Power BI sales dashboard used every morning by 25 category managers, \
                                replacing six manual Excel reports.
                                Wrote SQL models in BigQuery for daily order, basket, and repeat-customer metrics \
                                across 40 dark stores.
                                Analysed a free-delivery A/B test that supported a policy change, lifting average \
                                order value by 7%.
                                Automated the weekly inventory ageing report with Python, saving the supply team \
                                about four hours a week.
                                Found a duplicate-order bug in app event data and worked with engineering to fix tracking."""),
                        new ExperienceEntry("Insightly Analytics, Noida", "Business Analyst Intern",
                                "Jan 2024 – May 2024",
                                """
                                Cleaned and merged survey data from 12,000 respondents for a consumer goods client.
                                Built Tableau views on regional brand awareness that went into the final client deck.
                                Documented data definitions so the team could reuse the pipeline on the next survey wave.""")),
                List.of(new EducationEntry("Guru Gobind Singh Indraprastha University",
                        "B.Tech in Information Technology — CGPA 8.1/10", "2020 – 2024",
                        """
                        Final-year project: demand forecasting for a college canteen using time-series models
                        Head of the Data Science Club: ran a Kaggle study group for 40 students""",
                        "New Delhi")),
                List.of(
                        new ProjectEntry("Customer Churn Analysis",
                                """
                                Logistic regression model on 7,000 telecom customer records with 81% accuracy.
                                Summarised churn drivers in a one-page Power BI report for non-technical readers."""),
                        new ProjectEntry("IPL Auction Insights",
                                """
                                Scraped and cleaned ten seasons of auction and performance data with Python.
                                Built a Tableau dashboard comparing player price against runs and wickets per match."""),
                        new ProjectEntry("Delhi Air Quality Tracker",
                                """
                                Python pipeline that pulls daily AQI data from public sources into a SQLite database.
                                Power BI report showing seasonal trends and the worst-affected areas across 35 stations.""")),
                List.of("Google Data Analytics Professional Certificate", "Microsoft Certified: Power BI Data Analyst Associate"));
    }

    static BuilderExportRequest campus() {
        return new BuilderExportRequest("campus", "Joel Thomas", null,
                "joel.thomas@example.com", "+91 99887 76655", "Kochi, Kerala", null, null,
                "Final-year computer science student looking for a software developer role. Built and shipped "
                        + "full-stack projects used by classmates, interned on a production web app, and enjoy "
                        + "working in small teams that release often.",
                List.of("Java", "Python", "JavaScript", "React", "Node.js", "Express", "MongoDB", "MySQL",
                        "HTML", "CSS", "Git", "GitHub Actions", "Postman", "Data Structures and Algorithms"),
                List.of(
                        new ExperienceEntry("Techvantage Labs, Kochi", "Web Development Intern",
                                "May 2025 – Jul 2025",
                                """
                                Built React screens for a clinic appointment app, including booking and reminder settings.
                                Added Node.js endpoints for doctor availability with input validation and tests.
                                Fixed 20+ UI and API bugs from the team's backlog during the eight-week internship.
                                Presented a demo of the booking flow to the client in the final sprint review."""),
                        new ExperienceEntry("IEEE Student Branch, Rajagiri School of Engineering", "Technical Lead",
                                "Aug 2024 – Present",
                                """
                                Led a team of six to build the event registration site for a 1,200-participant tech fest.
                                Ran weekly Git and React workshops for 60+ first- and second-year students.""")),
                List.of(
                        new EducationEntry("Rajagiri School of Engineering and Technology (APJ Abdul Kalam Technological University)",
                                "B.Tech in Computer Science and Engineering — CGPA 8.7/10 (till 7th semester)",
                                "2022 – 2026",
                                """
                                Coursework: Data Structures, Algorithms, DBMS, Operating Systems, Computer Networks, \
                                Software Engineering
                                Department rank 4 of 120 in the 6th semester""", "Kochi"),
                        new EducationEntry("Bhavan's Adarsha Vidyalaya", "Class XII (CBSE) — 93.4%", "2022",
                                null, "Kochi")),
                List.of(
                        new ProjectEntry("CampusMart — Student Marketplace",
                                """
                                Full-stack app (React, Node.js, MongoDB) for buying and selling used books and gear on campus.
                                Added chat between buyer and seller and image uploads; 350 students signed up in the first term.
                                Set up GitHub Actions to run tests and deploy on every merge to main."""),
                        new ProjectEntry("Attendance Tracker with Face Recognition",
                                """
                                Python and OpenCV system that marks attendance from a classroom camera snapshot.
                                Reached 92% matching accuracy on a test set of 45 students under normal lighting."""),
                        new ProjectEntry("Bus Route Finder for Kochi",
                                """
                                Java desktop app that suggests the shortest bus route between two stops using Dijkstra's algorithm.""")),
                List.of("NPTEL: Programming, Data Structures and Algorithms using Python (Elite)",
                        "HackerRank: Problem Solving (Intermediate)"));
    }

    static BuilderExportRequest centered() {
        return new BuilderExportRequest("centered", "Lydia Abraham", null,
                "lydia.abraham@example.com", "+91 98450 12345", "Bengaluru, Karnataka", null, null,
                "Quality assurance engineer with two years of experience testing web and mobile apps for a "
                        + "fintech product. Writes clear test cases, automates regression suites, and works closely "
                        + "with developers to stop defects reaching customers.",
                List.of("Manual testing", "Selenium WebDriver", "Java", "TestNG", "Postman", "REST Assured",
                        "Appium", "JIRA", "SQL", "Git", "Jenkins", "Agile / Scrum", "Test case design", "Regression testing"),
                List.of(
                        new ExperienceEntry("Moneyleaf Fintech Pvt. Ltd., Bengaluru", "QA Engineer",
                                "Aug 2024 – Present",
                                """
                                Own testing for the loan application flow on web and Android, across five releases a month.
                                Automated 180 regression cases with Selenium and TestNG, cutting regression time \
                                from two days to four hours.
                                Built REST Assured API tests for the KYC service and added them to the Jenkins pipeline.
                                Logged and tracked 300+ defects in JIRA, with clear steps that helped developers fix \
                                them faster.
                                Ran test planning in sprint meetings and kept the release sign-off checklist up to date."""),
                        new ExperienceEntry("Brightpath Software Services, Mysuru", "QA Trainee",
                                "Jan 2024 – Jul 2024",
                                """
                                Wrote and executed test cases for a school management web app used by 40 schools.
                                Tested Android builds on eight devices with Appium and reported layout issues by screen size.
                                Verified data in MySQL after each release to catch migration errors early.
                                Prepared a smoke-test checklist that the support team now runs after every deployment.""")),
                List.of(new EducationEntry("Christ University", "B.Sc. in Computer Science — CGPA 8.2/10",
                        "2021 – 2024",
                        """
                        Final-year project: automated test suite for an online exam portal
                        Volunteer, Christ University Software Testing Club: ran testing workshops for juniors""", "Bengaluru")),
                List.of(
                        new ProjectEntry("E-commerce Test Automation Framework",
                                """
                                Page Object Model framework in Java with Selenium, TestNG, and Extent reports.
                                Covered login, search, cart, and checkout across Chrome and Firefox."""),
                        new ProjectEntry("API Test Collection for a Public Weather API",
                                """
                                Postman collection with 40 tests, environment variables, and Newman runs in CI.
                                Added schema checks so a changed response field fails the build immediately."""),
                        new ProjectEntry("Mobile Test Suite for a Food Ordering App",
                                """
                                Appium tests in Java for sign-up, menu search, cart, and payment screens on Android.
                                Ran the suite on three screen sizes and documented 25 defects with screenshots.""")),
                List.of("ISTQB Certified Tester Foundation Level (CTFL)", "Selenium WebDriver with Java — Udemy"));
    }

    static BuilderExportRequest headerBand() {
        return new BuilderExportRequest("header-band", "Arjun Mehta", null,
                "arjun.mehta@example.com", "+91 90000 12345", "Ahmedabad, Gujarat", null, null,
                "Frontend developer with 18 months of experience building fast, accessible React interfaces for "
                        + "SaaS products. Cares about performance, clean components, and close work with designers "
                        + "to ship polished screens.",
                List.of("JavaScript", "TypeScript", "React", "Next.js", "Redux Toolkit", "HTML5", "CSS3",
                        "Tailwind CSS", "REST APIs", "Jest", "React Testing Library", "Figma", "Git", "Vercel",
                        "Web accessibility (WCAG)"),
                List.of(
                        new ExperienceEntry("Cloudledger Software, Ahmedabad", "Frontend Developer",
                                "Jun 2024 – Present",
                                """
                                Built the invoicing and reports screens of a GST billing SaaS used by 2,000+ small businesses.
                                Reduced dashboard load time from 4.1 s to 1.6 s with code splitting and memoised charts.
                                Created a shared component library in React and TypeScript now used across three products.
                                Raised accessibility score from 68 to 96 by fixing labels, focus order, and contrast.
                                Reviewed pull requests and mentored two interns on testing with Jest and React Testing Library."""),
                        new ExperienceEntry("Pixelcraft Studio, Vadodara", "Frontend Intern",
                                "Dec 2023 – May 2024",
                                """
                                Converted Figma designs into responsive pages for four client websites.
                                Built a booking form with validation and connected it to the client's REST API.
                                Improved Lighthouse performance scores above 90 on all delivered pages.
                                Fixed cross-browser layout issues on Safari and older Android browsers.""")),
                List.of(new EducationEntry("Nirma University, Institute of Technology",
                        "B.Tech in Computer Engineering — CGPA 8.3/10", "2020 – 2024",
                        """
                        Coursework: Web Technologies, Human-Computer Interaction, DBMS
                        Design lead for the college tech fest website, visited by 15,000 students""", "Ahmedabad")),
                List.of(
                        new ProjectEntry("Trip Planner for Gujarat Heritage Sites",
                                """
                                Next.js app with maps, day-wise itineraries, and offline support, deployed on Vercel.
                                Used by 500+ visitors in the first month after sharing in travel groups."""),
                        new ProjectEntry("Habit Tracker PWA",
                                """
                                React and IndexedDB progressive web app with streaks, reminders, and dark mode.
                                Works offline and installs to the home screen on Android and iOS."""),
                        new ProjectEntry("Open-Source Contribution — React Date Picker",
                                """
                                Fixed keyboard navigation and screen-reader labels in a popular date picker library.
                                Two pull requests merged after review by the maintainers.""")),
                List.of("Meta Front-End Developer Professional Certificate"));
    }
}
