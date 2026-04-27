package com.authenticself.admin;

/**
 * Unchecked domain exception carrying an {@link AdminErrorCode}
 * (UC-03-admin-overview FR-11).
 * <p>
 * Translated to the shared {@code ErrorResponse} envelope by
 * {@link AdminExceptionAdvice}. Mirrors
 * {@link com.authenticself.wishlist.WishlistException}.
 */
public class AdminException extends RuntimeException {

    private final AdminErrorCode code;

    public AdminException(AdminErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public AdminException(AdminErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AdminErrorCode code() { return code; }
}
