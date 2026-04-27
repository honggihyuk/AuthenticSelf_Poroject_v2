package com.authenticself.wishlist;

/**
 * Unchecked domain exception carrying a {@link WishlistErrorCode}
 * (UC-02-wishlist FR-9).
 * <p>
 * Translated to the shared {@code ErrorResponse} envelope by
 * {@link WishlistExceptionAdvice}.
 */
public class WishlistException extends RuntimeException {

    private final WishlistErrorCode code;

    public WishlistException(WishlistErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public WishlistException(WishlistErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public WishlistErrorCode code() { return code; }
}
