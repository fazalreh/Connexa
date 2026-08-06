package com.connexa.api.domain.checkin;

/**
 * Raised when a scanned pass cannot be honoured.
 *
 * <p>The message is written for the person holding the scanner, and deliberately does not
 * distinguish a forged signature from an unknown one: the door is a place where someone may
 * be probing, and "wrong signature" is a more useful answer to an attacker than to a steward.
 */
public class InvalidCheckInPassException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCheckInPassException(String message) {
        super(message);
    }
}
