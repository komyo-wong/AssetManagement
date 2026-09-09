package com.assetmanagement.mqtt.application;

import com.assetmanagement.mqtt.domain.MqttRouteDirection;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
public class MqttTopicValidator {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,119}");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(?:password|passwd|token|api[-_]?key|authorization)\\s*[=:]"
    );

    public void validate(
            MqttRouteDirection direction,
            String topicPattern,
            String messageType,
            String parserKey,
            int qos,
            boolean retained
    ) {
        if (direction == null) {
            invalid("MQTT route direction is required");
        }
        validateTopic(direction, topicPattern);
        validateIdentifier(messageType, "message type");
        validateIdentifier(parserKey, "parser key");
        if (qos < 0 || qos > 2) {
            invalid("MQTT QoS must be between 0 and 2");
        }
        if (retained && direction != MqttRouteDirection.DOWNLINK) {
            invalid("Retain can only be configured for a downlink route");
        }
    }

    public boolean overlaps(
            MqttRouteDirection direction,
            String left,
            String right
    ) {
        if (direction == MqttRouteDirection.DOWNLINK) {
            return left.equals(right);
        }
        String[] leftLevels = left.split("/", -1);
        String[] rightLevels = right.split("/", -1);
        return intersects(leftLevels, 0, rightLevels, 0);
    }

    private static boolean intersects(String[] left, int leftIndex, String[] right, int rightIndex) {
        if (leftIndex < left.length && left[leftIndex].equals("#")) {
            return true;
        }
        if (rightIndex < right.length && right[rightIndex].equals("#")) {
            return true;
        }
        if (leftIndex == left.length || rightIndex == right.length) {
            return leftIndex == left.length && rightIndex == right.length;
        }
        String leftLevel = left[leftIndex];
        String rightLevel = right[rightIndex];
        if (!leftLevel.equals("+") && !rightLevel.equals("+") && !leftLevel.equals(rightLevel)) {
            return false;
        }
        return intersects(left, leftIndex + 1, right, rightIndex + 1);
    }

    private static void validateTopic(MqttRouteDirection direction, String topic) {
        if (topic == null || topic.isEmpty() || topic.length() > 500) {
            invalid("MQTT topic must contain between 1 and 500 characters");
        }
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(topic)
                || topic.indexOf('\0') >= 0
                || topic.chars().anyMatch(character -> Character.isISOControl(character))) {
            invalid("MQTT topic contains invalid characters");
        }
        if (SECRET_ASSIGNMENT.matcher(topic).find()) {
            invalid("MQTT topic cannot contain credentials or secret assignments");
        }

        if (direction == MqttRouteDirection.DOWNLINK) {
            if (topic.indexOf('+') >= 0 || topic.indexOf('#') >= 0) {
                invalid("Downlink publish topics cannot contain wildcards");
            }
            return;
        }

        String[] levels = topic.split("/", -1);
        for (int index = 0; index < levels.length; index++) {
            String level = levels[index];
            if (level.indexOf('#') >= 0 && (!level.equals("#") || index != levels.length - 1)) {
                invalid("The # wildcard must occupy the final topic level");
            }
            if (level.indexOf('+') >= 0 && !level.equals("+")) {
                invalid("The + wildcard must occupy an entire topic level");
            }
        }
    }

    private static void validateIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            invalid("MQTT " + label + " must be a stable identifier");
        }
    }

    private static void invalid(String message) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
