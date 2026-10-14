package com.resqora.resqora_backend.demo;

import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.AiUsageRepository;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.ResumeService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

// Demo mode (RESQORA_DEMO_ENABLED=true): creates a ready-to-show account with analysed resumes.
// Runs on every startup and resets the account: resumes (with analyses, AI answers, job suggestions)
// and AI usage are deleted, name and password restored, and the sample resumes uploaded again.
@Component
@ConditionalOnProperty(name = "resqora.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    public static final String DEMO_EMAIL = "demo@resqora.app";
    static final String DEMO_NAME = "Demo User";
    static final List<String> DEMO_FILES = List.of("01_baseline.pdf", "02_two_column.pdf", "03_messy.pdf");
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResumeService resumeService;
    private final ResumeRepository resumeRepository;
    private final AiUsageRepository aiUsageRepository;
    private final TransactionTemplate transactionTemplate;
    private final String demoPassword;

    public DemoDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
            ResumeService resumeService, ResumeRepository resumeRepository, AiUsageRepository aiUsageRepository,
            TransactionTemplate transactionTemplate,
            @Value("${resqora.demo.password:Demo1234}") String demoPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.resumeService = resumeService;
        this.resumeRepository = resumeRepository;
        this.aiUsageRepository = aiUsageRepository;
        this.transactionTemplate = transactionTemplate;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        User user = transactionTemplate.execute(status -> {
            User existing = userRepository.findByEmail(DEMO_EMAIL).orElse(null);
            if (existing == null) {
                return userRepository.saveAndFlush(
                        new User(DEMO_EMAIL, passwordEncoder.encode(demoPassword), DEMO_NAME));
            }
            // reset: analyses, AI answers and job suggestions cascade from resumes
            resumeRepository.deleteAll(resumeRepository.findByUserIdOrderByCreatedAtDescIdDesc(existing.getId()));
            aiUsageRepository.deleteByUserId(existing.getId());
            existing.setName(DEMO_NAME);
            existing.setPasswordHash(passwordEncoder.encode(demoPassword));
            return userRepository.saveAndFlush(existing);
        });
        var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
        for (String name : DEMO_FILES) {
            try {
                byte[] bytes = new ClassPathResource("demo/" + name).getContentAsByteArray();
                // the normal upload path, so extraction + analysis are saved exactly as for a real user
                resumeService.uploadResume(new BytesFile(name, bytes), authentication);
            } catch (IOException | RuntimeException exception) {
                log.warn("Demo resume {} could not be seeded: {}", name, exception.toString());
            }
        }
        log.info("Demo user {} ready (data reset)", DEMO_EMAIL);
    }

    // minimal in-memory MultipartFile (spring-test's MockMultipartFile is test-scope only)
    private record BytesFile(String name, byte[] bytes) implements MultipartFile {
        public String getName() { return "file"; }
        public String getOriginalFilename() { return name; }
        public String getContentType() { return "application/pdf"; }
        public boolean isEmpty() { return bytes.length == 0; }
        public long getSize() { return bytes.length; }
        public byte[] getBytes() { return bytes; }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        public void transferTo(java.io.File dest) throws IOException { Files.write(dest.toPath(), bytes); }
        public void transferTo(Path dest) throws IOException { Files.write(dest, bytes); }
    }
}
