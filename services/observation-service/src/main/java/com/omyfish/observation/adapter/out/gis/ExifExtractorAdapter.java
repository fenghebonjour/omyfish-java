package com.omyfish.observation.adapter.out.gis;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.omyfish.observation.domain.model.valueobject.ExifMetadata;
import com.omyfish.observation.domain.model.valueobject.GpsCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

@Component
public class ExifExtractorAdapter {

    private static final Logger log = LoggerFactory.getLogger(ExifExtractorAdapter.class);

    public record Extraction(ExifMetadata metadata, GpsCoordinates gps) {}

    public Extraction extract(InputStream imageStream) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(imageStream);

            ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            Instant capturedAt = null;
            Integer width = null, height = null;
            Double focalLength = null, aperture = null;
            Integer iso = null;
            if (subIfd != null) {
                var date = subIfd.getDateOriginal();
                if (date != null) capturedAt = date.toInstant();
                width = subIfd.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
                height = subIfd.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
                var fl = subIfd.getRational(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
                if (fl != null) focalLength = fl.doubleValue();
                var ap = subIfd.getRational(ExifSubIFDDirectory.TAG_FNUMBER);
                if (ap != null) aperture = ap.doubleValue();
                iso = subIfd.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
            }

            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            String cameraModel = null;
            if (ifd0 != null) {
                String make = ifd0.getString(ExifIFD0Directory.TAG_MAKE);
                String model = ifd0.getString(ExifIFD0Directory.TAG_MODEL);
                cameraModel = (make != null && model != null) ? make + " " + model
                    : (model != null ? model : make);
            }

            GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            GpsCoordinates gps = GpsCoordinates.unknown();
            if (gpsDir != null) {
                GeoLocation location = gpsDir.getGeoLocation();
                if (location != null) {
                    try {
                        gps = GpsCoordinates.of(location.getLatitude(), location.getLongitude());
                    } catch (IllegalArgumentException e) {
                        log.warn("Discarding out-of-range EXIF GPS coordinates ({}, {}): {}",
                            location.getLatitude(), location.getLongitude(), e.getMessage());
                    }
                }
            }

            return new Extraction(
                new ExifMetadata(capturedAt, cameraModel, width, height, focalLength, aperture, iso),
                gps
            );
        } catch (ImageProcessingException | IOException e) {
            // Missing or corrupt metadata is expected for some uploads — the
            // observation is still usable without EXIF, so degrade instead of failing.
            log.warn("Could not read image metadata — continuing without EXIF/GPS: {}", e.toString());
            return new Extraction(ExifMetadata.empty(), GpsCoordinates.unknown());
        }
    }
}
