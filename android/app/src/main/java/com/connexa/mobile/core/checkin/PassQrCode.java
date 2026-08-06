package com.connexa.mobile.core.checkin;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.EnumMap;
import java.util.Map;

/**
 * Turns a signed pass into the square a scanner can read.
 *
 * <p>Produces a matrix rather than a bitmap so the encoding has no Android dependency and can
 * be tested directly. Drawing it is the screen's job.
 */
public final class PassQrCode {

    /**
     * Error correction is deliberately high rather than the usual default.
     *
     * <p>The code is read off a phone screen at a door: fingerprints, glare, a cracked
     * protector and a hurried scan all remove information. The stronger level costs some
     * density but recovers from roughly a third of the square being unreadable, which is the
     * difference between a queue that moves and one that stalls.
     */
    private static final ErrorCorrectionLevel CORRECTION = ErrorCorrectionLevel.H;

    /** Without this, the writer adds a wide quiet zone and the drawn code shrinks. */
    private static final int QUIET_ZONE_MODULES = 2;

    private PassQrCode() {
    }

    /**
     * @param size requested pixel size of the square
     * @throws IllegalArgumentException if the pass is empty, since an empty code would scan
     *     as a valid-looking nothing rather than failing visibly
     */
    public static BitMatrix encode(String pass, int size) {
        if (pass == null || pass.isBlank()) {
            throw new IllegalArgumentException("pass is required");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, CORRECTION);
        hints.put(EncodeHintType.MARGIN, QUIET_ZONE_MODULES);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        try {
            return new QRCodeWriter().encode(pass, BarcodeFormat.QR_CODE, size, size, hints);
        } catch (WriterException unencodable) {
            // The pass is base64url text of a bounded length, so this is a broken library
            // rather than a condition the door can do anything about.
            throw new IllegalStateException("Could not render the pass", unencodable);
        }
    }
}
