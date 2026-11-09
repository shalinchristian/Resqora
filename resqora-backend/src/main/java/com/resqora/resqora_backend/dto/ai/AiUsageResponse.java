package com.resqora.resqora_backend.dto.ai;

// GET /api/ai/usage. resumeChat and dashboardChat are the two separate daily limits.
// usedToday / limit / remainingToday are both chats added together, kept so older frontends still work.
public record AiUsageResponse(int usedToday, int limit, int remainingToday, Bucket resumeChat, Bucket dashboardChat) {

    public record Bucket(int usedToday, int limit, int remainingToday) {
        public static Bucket of(long used, int limit) {
            return new Bucket((int) used, limit, (int) Math.max(0, limit - used));
        }
    }

    public static AiUsageResponse of(Bucket resumeChat, Bucket dashboardChat) {
        return new AiUsageResponse(resumeChat.usedToday() + dashboardChat.usedToday(),
                resumeChat.limit() + dashboardChat.limit(),
                resumeChat.remainingToday() + dashboardChat.remainingToday(), resumeChat, dashboardChat);
    }
}
