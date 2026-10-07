package study.yume.dto.account.request;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
