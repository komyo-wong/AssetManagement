package com.assetmanagement.iam;

import com.assetmanagement.iam.domain.User;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserIdentityService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;

    public UserIdentityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void assertUsernameAvailable(String username, UUID exceptUserId) {
        String value = requireText(username, "账号不能为空");
        if (value.length() > 80) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "账号过长");
        }
        assertUnused(value, exceptUserId, "该账号已被使用");
    }

    public void assertEmailAvailable(String email, UUID exceptUserId) {
        String value = normalizeEmail(email);
        assertUnused(value, exceptUserId, "该邮箱已被使用");
    }

    public static String normalizeEmail(String email) {
        String value = requireText(email, "请填写邮箱");
        if (value.length() > 254) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "邮箱过长");
        }
        if (!EMAIL.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "邮箱格式不正确");
        }
        return value;
    }

    private void assertUnused(String identity, UUID exceptUserId, String message) {
        if (taken(userRepository.findByUsernameIgnoreCase(identity).orElse(null), exceptUserId)
                || taken(userRepository.findByEmailIgnoreCase(identity).orElse(null), exceptUserId)) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }

    private static boolean taken(User user, UUID exceptUserId) {
        if (user == null) {
            return false;
        }
        return exceptUserId == null || !exceptUserId.equals(user.getId());
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value.trim();
    }
}
