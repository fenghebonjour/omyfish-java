package com.omyfish.species.domain.exception;

/** Storing or reading an image in the object store failed. */
public class ImageStorageException extends RuntimeException {

    public ImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
