package com.project.handongjudge.common.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final String errorCode;

    public CustomException(String message) {
        super(message);
        this.errorCode = null;
    }

    public CustomException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}